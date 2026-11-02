import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { voiceStore } from "../store/voiceStore";
import { voiceChatManager } from "./VoiceChatManager";
import { Player } from "../types/room";
import { createVoicePipeline } from "./voicePipeline";
import { wsClient } from "../websocket/WebSocketClient";

vi.mock("./voicePipeline", () => ({ createVoicePipeline: vi.fn() }));

describe("WebRTC Voice Chat - voiceStore", () => {
  beforeEach(() => {
    voiceStore.reset();
  });

  it("initializes with voice room enabled and mic off", () => {
    const state = voiceStore.getState();
    expect(state.isVoiceRoomEnabled).toBe(true);
    expect(state.isMicOn).toBe(false);
    expect(state.isMuted).toBe(false);
    expect(state.isDeafened).toBe(false);
    expect(state.isLocalSpeaking).toBe(false);
    expect(Object.keys(state.speakingPlayers).length).toBe(0);
  });

  it("updates room voice enabled flag", () => {
    voiceStore.setVoiceRoomEnabled(true);
    expect(voiceStore.getState().isVoiceRoomEnabled).toBe(true);
    voiceStore.setVoiceRoomEnabled(false);
    expect(voiceStore.getState().isVoiceRoomEnabled).toBe(false);
  });

  it("manages local mic and mute state", () => {
    voiceStore.setMicOn(true);
    expect(voiceStore.getState().isMicOn).toBe(true);

    voiceStore.setMuted(true);
    expect(voiceStore.getState().isMuted).toBe(true);

    voiceStore.setDeafened(true);
    expect(voiceStore.getState().isDeafened).toBe(true);
  });

  it("tracks speaking indicators per player", () => {
    voiceStore.setPlayerSpeaking("player-1", true);
    expect(voiceStore.getState().speakingPlayers["player-1"]).toBe(true);

    voiceStore.setPlayerSpeaking("player-1", false);
    expect(voiceStore.getState().speakingPlayers["player-1"]).toBeUndefined();
  });

  it("manages peer voice states and peer volume levels", () => {
    voiceStore.setPeerVoiceState("player-2", {
      isMuted: true,
      isDeafened: false,
      isSpeaking: false,
    });
    expect(voiceStore.getState().peerVoiceStates["player-2"].isMuted).toBe(true);

    voiceStore.setPeerVolume("player-2", 0.75);
    expect(voiceStore.getState().peerVolumes["player-2"]).toBe(0.75);

    // Clamp volume to [0, 1]
    voiceStore.setPeerVolume("player-2", 1.5);
    expect(voiceStore.getState().peerVolumes["player-2"]).toBe(1.0);

    voiceStore.removePeer("player-2");
    expect(voiceStore.getState().peerVoiceStates["player-2"]).toBeUndefined();
    expect(voiceStore.getState().peerVolumes["player-2"]).toBeUndefined();
  });
});

describe("WebRTC Voice Chat - VoiceChatManager", () => {
  let mockAudio: any;

  beforeEach(() => {
    voiceStore.reset();
    mockAudio = {
      autoplay: true,
      muted: false,
      volume: 1.0,
      play: vi.fn().mockResolvedValue(undefined),
      pause: vi.fn(),
      srcObject: null,
      style: {},
    };
    vi.stubGlobal("Audio", vi.fn(() => mockAudio));

    class MockMediaStream {
      tracks: any[] = [];
      getTracks() {
        return this.tracks;
      }
      addTrack(t: any) {
        this.tracks.push(t);
      }
    }
    vi.stubGlobal("MediaStream", MockMediaStream);

    const mockRTCPeerConnection = vi.fn().mockImplementation(() => ({
      getSenders: vi.fn().mockReturnValue([]),
      addTrack: vi.fn(),
      addTransceiver: vi.fn(),
      removeTrack: vi.fn(),
      close: vi.fn(),
      setLocalDescription: vi.fn().mockResolvedValue(undefined),
      setRemoteDescription: vi.fn().mockResolvedValue(undefined),
      addIceCandidate: vi.fn().mockResolvedValue(undefined),
      signalingState: "stable",
      connectionState: "connected",
      localDescription: { type: "offer", sdp: "v=0..." },
    }));
    vi.stubGlobal("RTCPeerConnection", mockRTCPeerConnection);
  });

  afterEach(() => {
    voiceChatManager.cleanup();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("shares one pending microphone request across repeated clicks", async () => {
    let resolveCapture!: (stream: any) => void;
    const rawTrack = { stop: vi.fn(), kind: "audio", enabled: true };
    const raw = { getTracks: () => [rawTrack], getAudioTracks: () => [rawTrack] };
    const getUserMedia = vi.fn(() => new Promise<any>(resolve => { resolveCapture = resolve; }));
    vi.stubGlobal("navigator", { mediaDevices: { getUserMedia } });
    vi.mocked(createVoicePipeline).mockResolvedValue({ stream: raw as any, dispose: vi.fn() });
    const first = voiceChatManager.startMic();
    const second = voiceChatManager.startMic();
    expect(first).toBe(second);
    expect(getUserMedia).toHaveBeenCalledTimes(1);
    resolveCapture(raw);
    expect(await first).toBe(true);
  });

  it("stops a late permission result after the user turned mic off", async () => {
    let resolveCapture!: (stream: any) => void;
    const track = { stop: vi.fn() };
    vi.stubGlobal("navigator", { mediaDevices: {
      getUserMedia: vi.fn(() => new Promise<any>(resolve => { resolveCapture = resolve; })),
    } });
    const pending = voiceChatManager.startMic();
    voiceChatManager.stopMic();
    resolveCapture({ getTracks: () => [track] });
    expect(await pending).toBe(false);
    expect(track.stop).toHaveBeenCalledTimes(1);
    expect(voiceStore.getState().isMicOn).toBe(false);
  });

  it("unlocks Web Audio before awaiting microphone permission", async () => {
    const resume = vi.fn().mockResolvedValue(undefined);
    const close = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("AudioContext", vi.fn(() => ({ resume, close, state: "running" })));
    let resolveCapture!: (stream: any) => void;
    vi.stubGlobal("navigator", { mediaDevices: {
      getUserMedia: vi.fn(() => new Promise<any>(resolve => { resolveCapture = resolve; })),
    } });
    const pending = voiceChatManager.startMic();
    expect(resume).toHaveBeenCalledOnce();
    voiceChatManager.stopMic();
    expect(close).toHaveBeenCalled();
    resolveCapture({ getTracks: () => [{ stop: vi.fn() }] });
    expect(await pending).toBe(false);
  });

  it("releases hardware immediately if stopped while the filter is loading", async () => {
    const track = { stop: vi.fn(), kind: "audio", enabled: true };
    const raw = { getTracks: () => [track], getAudioTracks: () => [track] };
    let resolvePipeline!: (pipeline: any) => void;
    const dispose = vi.fn();
    vi.stubGlobal("navigator", { mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(raw) } });
    vi.mocked(createVoicePipeline).mockImplementationOnce(() => new Promise(resolve => { resolvePipeline = resolve; }));
    const pending = voiceChatManager.startMic();
    await vi.waitFor(() => expect(resolvePipeline).toBeTypeOf("function"));
    voiceChatManager.stopMic();
    expect(track.stop).toHaveBeenCalled();
    resolvePipeline({ stream: raw, dispose });
    expect(await pending).toBe(false);
    expect(dispose).toHaveBeenCalledOnce();
    expect(voiceStore.getState().isMicOn).toBe(false);
  });

  it("syncs peers according to room roster", () => {
    voiceChatManager.initSession("player-local", "room-1");

    const players: Player[] = [
      { playerId: "player-local", username: "Local" },
      { playerId: "player-remote-1", username: "Remote 1" },
      { playerId: "player-remote-2", username: "Remote 2" },
    ];

    voiceChatManager.syncPeers(players);

    // Remove remote-2
    const remainingPlayers: Player[] = [
      { playerId: "player-local", username: "Local" },
      { playerId: "player-remote-1", username: "Remote 1" },
    ];
    voiceChatManager.syncPeers(remainingPlayers);
  });

  it("handles incoming signals for remote peers", async () => {
    voiceChatManager.initSession("player-local", "room-1");

    await voiceChatManager.handleSignal("player-remote-1", {
      type: "description",
      sdp: { type: "offer", sdp: "v=0\r\no=..." },
    });

    await voiceChatManager.handleSignal("player-remote-1", {
      type: "candidate",
      candidate: { candidate: "candidate:1...", sdpMid: "0" },
    });
  });

  it.each(["offer", "answer"] as const)("preserves prototype-backed SDP type for %s", async (type) => {
    voiceChatManager.initSession("player-local", "room-1");
    voiceChatManager.syncPeers([{ playerId: "player-remote", username: "Remote" }]);
    const pc = vi.mocked(RTCPeerConnection).mock.results[0].value;
    // Reproduce native RTCSessionDescription, unlike a plain object mock.
    pc.localDescription = Object.create({
      get type() { return type; },
      get sdp() { return "v=0\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 useinbandfec=1\r\n"; },
    });
    const send = vi.spyOn(wsClient, "sendRaw").mockImplementation(() => {});
    await pc.onnegotiationneeded();
    const message = JSON.parse(send.mock.calls[0][0] as string);
    expect(message.signal.sdp.type).toBe(type);
    expect(message.signal.sdp.sdp).toContain("usedtx=1");
  });

  it("rejects malformed SDP before creating a peer", async () => {
    voiceChatManager.initSession("player-local", "room-1");
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    await voiceChatManager.handleSignal("player-remote", {
      type: "description", sdp: { sdp: "v=0" } as RTCSessionDescriptionInit,
    });
    expect(RTCPeerConnection).not.toHaveBeenCalled();
    expect(warn).toHaveBeenCalled();
  });

  it("accepts an offer arriving while the preceding answer is being applied", async () => {
    voiceChatManager.initSession("z-local", "room-1");
    voiceChatManager.syncPeers([{ playerId: "a-remote", username: "Remote" }]);
    const pc = vi.mocked(RTCPeerConnection).mock.results[0].value;
    pc.signalingState = "have-local-offer";
    let finishAnswer!: () => void;
    pc.setRemoteDescription.mockImplementationOnce(() => new Promise<void>(resolve => {
      finishAnswer = () => { pc.signalingState = "stable"; resolve(); };
    }));
    const answer = voiceChatManager.handleSignal("a-remote", {
      type: "description", sdp: { type: "answer", sdp: "v=0" },
    });
    await vi.waitFor(() => expect(pc.setRemoteDescription).toHaveBeenCalledTimes(1));
    const offer = voiceChatManager.handleSignal("a-remote", {
      type: "description", sdp: { type: "offer", sdp: "v=0" },
    });
    expect(pc.setRemoteDescription).toHaveBeenCalledTimes(1);
    finishAnswer();
    await Promise.all([answer, offer]);
    expect(pc.setRemoteDescription.mock.calls.map((call: any[]) => call[0].type)).toEqual(["answer", "offer"]);
  });

  it("reports recent audio flow without copying network addresses", async () => {
    vi.useFakeTimers();
    try {
      voiceChatManager.initSession("local", "room-1");
      voiceChatManager.syncPeers([{ playerId: "remote", username: "Remote" }]);
      const pc = vi.mocked(RTCPeerConnection).mock.results[0].value;
      const reports = (sent: number, received: number, energy: number) => new Map<string, any>([
        ["out", { type: "outbound-rtp", kind: "audio", packetsSent: sent }],
        ["in", { type: "inbound-rtp", kind: "audio", packetsReceived: received, totalAudioEnergy: energy }],
        ["candidate", { type: "local-candidate", address: "private-network-address" }],
      ]);
      pc.getStats = vi.fn().mockResolvedValueOnce(reports(10, 20, 0.1)).mockResolvedValueOnce(reports(15, 26, 0.2));
      const pending = voiceChatManager.getDiagnostics();
      await vi.advanceTimersByTimeAsync(1201);
      const result = await pending;
      expect(result.peers[0]).toMatchObject({ sentPackets: 5, receivedPackets: 6 });
      expect(result.peers[0].receivedEnergy).toBeCloseTo(0.1);
      expect(JSON.stringify(result)).not.toContain("private-network-address");
    } finally { vi.useRealTimers(); }
  });

  it("controls deafen and mute across peer audio elements", () => {
    voiceChatManager.initSession("player-local", "room-1");
    voiceChatManager.syncPeers([
      { playerId: "player-local", username: "Local" },
      { playerId: "player-remote-1", username: "Remote" },
    ]);

    voiceChatManager.setDeafen(true);
    expect(voiceStore.getState().isDeafened).toBe(true);

    voiceChatManager.setPeerVolume("player-remote-1", 0.5);
    expect(voiceStore.getState().peerVolumes["player-remote-1"]).toBe(0.5);
  });
});
