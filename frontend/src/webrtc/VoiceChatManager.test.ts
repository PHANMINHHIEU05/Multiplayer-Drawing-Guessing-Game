import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { voiceStore } from "../store/voiceStore";
import { voiceChatManager } from "./VoiceChatManager";
import { Player } from "../types/room";

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
