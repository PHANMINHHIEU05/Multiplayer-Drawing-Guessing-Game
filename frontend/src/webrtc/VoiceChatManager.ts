import { wsClient } from "../websocket/WebSocketClient";
import { MessageType } from "../websocket/protocol";
import { voiceStore } from "../store/voiceStore";
import { Player } from "../types/room";

export interface WebRTCSignalData {
  type?: "offer" | "answer" | "candidate" | "description";
  sdp?: RTCSessionDescriptionInit;
  candidate?: RTCIceCandidateInit;
}

interface PeerConnectionWrapper {
  playerId: string;
  pc: RTCPeerConnection;
  isPolite: boolean;
  makingOffer: boolean;
  ignoreOffer: boolean;
  audioElement: HTMLAudioElement;
  pendingCandidates: RTCIceCandidateInit[];
}

export class VoiceChatManager {
  private static instance: VoiceChatManager | null = null;

  private localStream: MediaStream | null = null;
  private audioContext: AudioContext | null = null;
  private vadInterval: ReturnType<typeof setInterval> | null = null;
  private peers: Map<string, PeerConnectionWrapper> = new Map();
  private localPlayerId: string | null = null;
  private roomId: string | null = null;
  private isSpeaking: boolean = false;
  private lastSpeakingChangeTime: number = 0;
  private audioUnlockInitialized: boolean = false;

  private constructor() {
    this.initAudioUnlock();
  }

  public static getInstance(): VoiceChatManager {
    if (!VoiceChatManager.instance) {
      VoiceChatManager.instance = new VoiceChatManager();
    }
    return VoiceChatManager.instance;
  }

  private initAudioUnlock() {
    if (this.audioUnlockInitialized || typeof window === "undefined") return;
    this.audioUnlockInitialized = true;

    const unlock = () => {
      for (const peer of this.peers.values()) {
        if (
          peer.audioElement &&
          peer.audioElement.paused &&
          peer.audioElement.srcObject
        ) {
          peer.audioElement.play().catch(() => {});
        }
      }
    };

    window.addEventListener("click", unlock, { passive: true });
    window.addEventListener("keydown", unlock, { passive: true });
    window.addEventListener("touchstart", unlock, { passive: true });
  }

  private getIceServers(): RTCIceServer[] {
    const servers: RTCIceServer[] = [];
    const env = ((import.meta as any).env || {}) as Record<string, string>;

    // STUN config from env or fallback to multiple Google public STUN servers
    const stunEnv = env.VITE_STUN_URLS;
    if (stunEnv) {
      const urls = stunEnv
        .split(",")
        .map((s: string) => s.trim())
        .filter(Boolean);
      if (urls.length > 0) {
        servers.push({ urls });
      }
    } else {
      servers.push({
        urls: [
          "stun:stun.l.google.com:19302",
          "stun:stun1.l.google.com:19302",
          "stun:stun2.l.google.com:19302",
          "stun:stun3.l.google.com:19302",
          "stun:stun4.l.google.com:19302",
        ],
      });
    }

    // Optional TURN config from env
    const turnUrl = env.VITE_TURN_URL;
    const turnUser = env.VITE_TURN_USERNAME;
    const turnCred = env.VITE_TURN_CREDENTIAL;
    if (turnUrl && turnUser && turnCred) {
      servers.push({
        urls: turnUrl,
        username: turnUser,
        credential: turnCred,
      });
    }

    return servers;
  }

  public initSession(localPlayerId: string, roomId: string) {
    this.localPlayerId = localPlayerId;
    this.roomId = roomId;
  }

  /**
   * Request microphone permission and start local audio track.
   * Only called on explicit user unmute.
   */
  public async startMic(): Promise<boolean> {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      voiceStore.setMicPermission("unsupported");
      const isHttp =
        typeof window !== "undefined" &&
        window.location &&
        window.location.protocol === "http:" &&
        window.location.hostname !== "localhost" &&
        window.location.hostname !== "127.0.0.1";
      voiceStore.setMicError(
        isHttp
          ? "Microphone yêu cầu HTTPS (Trình duyệt chặn quyền truy cập mic trên HTTP qua IP)."
          : "Trình duyệt không hỗ trợ micro",
      );
      return false;
    }

    try {
      const constraints: MediaStreamConstraints = {
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          deviceId: voiceStore.getState().selectedDeviceId
            ? { exact: voiceStore.getState().selectedDeviceId! }
            : undefined,
        },
        video: false,
      };

      const stream = await navigator.mediaDevices.getUserMedia(constraints);
      this.localStream = stream;
      voiceStore.setMicPermission("granted");
      voiceStore.setMicOn(true);
      voiceStore.setMuted(false);
      voiceStore.setMicError(null);

      // Query input devices
      this.refreshAudioDevices();

      // Attach audio tracks to all existing peer connections
      const audioTrack = stream.getAudioTracks()[0];
      if (audioTrack) {
        for (const peer of this.peers.values()) {
          const senders = peer.pc.getSenders();
          const audioSender = senders.find(
            (s) => (s.track && s.track.kind === "audio") || !s.track,
          );
          if (audioSender) {
            audioSender.replaceTrack(audioTrack).catch((err) => {
              console.warn(`[WebRTC] replaceTrack error:`, err);
            });
          } else {
            peer.pc.addTrack(audioTrack, stream);
          }
        }
      }

      // Initialize Voice Activity Detection (VAD)
      this.setupVAD(stream);

      // Broadcast unmuted state
      this.sendVoiceStateUpdate({ isMuted: false, isSpeaking: false });
      return true;
    } catch (err: any) {
      console.warn("Failed to acquire microphone:", err);
      const isDenied =
        err.name === "NotAllowedError" || err.name === "PermissionDeniedError";
      voiceStore.setMicPermission(isDenied ? "denied" : "prompt");
      voiceStore.setMicError(
        isDenied
          ? "Bạn đã chặn quyền truy cập micro"
          : "Không thể kết nối tới micro: " + (err.message || ""),
      );
      voiceStore.setMicOn(false);
      return false;
    }
  }

  /**
   * Stop local mic track completely and detach from peers.
   */
  public stopMic() {
    if (this.vadInterval) {
      clearInterval(this.vadInterval);
      this.vadInterval = null;
    }
    if (this.audioContext && this.audioContext.state !== "closed") {
      this.audioContext.close().catch(() => {});
      this.audioContext = null;
    }

    if (this.localStream) {
      this.localStream.getTracks().forEach((track) => track.stop());
      this.localStream = null;
    }

    for (const peer of this.peers.values()) {
      const senders = peer.pc.getSenders();
      for (const sender of senders) {
        if (sender.track && sender.track.kind === "audio") {
          sender.replaceTrack(null).catch(() => {});
        }
      }
    }

    this.isSpeaking = false;
    if (this.localPlayerId) {
      voiceStore.setLocalSpeaking(false, this.localPlayerId);
    }
    voiceStore.setMicOn(false);
    voiceStore.setMuted(false);

    this.sendVoiceStateUpdate({ isMuted: true, isSpeaking: false });
  }

  public setMute(muted: boolean) {
    if (this.localStream) {
      const audioTrack = this.localStream.getAudioTracks()[0];
      if (audioTrack) {
        audioTrack.enabled = !muted;
      }
    }
    voiceStore.setMuted(muted);
    if (muted && this.isSpeaking) {
      this.isSpeaking = false;
      if (this.localPlayerId) {
        voiceStore.setLocalSpeaking(false, this.localPlayerId);
      }
    }
    this.sendVoiceStateUpdate({ isMuted: muted, isSpeaking: false });
  }

  public setDeafen(deafened: boolean) {
    voiceStore.setDeafened(deafened);
    for (const peer of this.peers.values()) {
      peer.audioElement.muted = deafened;
    }
    this.sendVoiceStateUpdate({ isDeafened: deafened });
  }

  public setPeerVolume(playerId: string, volume: number) {
    voiceStore.setPeerVolume(playerId, volume);
    const peer = this.peers.get(playerId);
    if (peer) {
      peer.audioElement.volume = Math.max(0, Math.min(1, volume));
    }
  }

  public async refreshAudioDevices() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
      return;
    }
    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const audioInputs = devices.filter((d) => d.kind === "audioinput");
      voiceStore.setAudioDevices(audioInputs);
    } catch {
      // Ignored
    }
  }

  /**
   * Synchronize WebRTC Mesh with the authoritative room player roster.
   */
  public syncPeers(players: Player[]) {
    if (!this.localPlayerId || !this.roomId) {
      return;
    }

    const currentPeerIds = new Set<string>();
    for (const player of players) {
      if (player.playerId === this.localPlayerId) {
        continue;
      }
      currentPeerIds.add(player.playerId);

      if (!this.peers.has(player.playerId)) {
        this.createPeerConnection(player.playerId);
      }
    }

    // Close peers who left
    for (const [peerId, peer] of this.peers.entries()) {
      if (!currentPeerIds.has(peerId)) {
        this.closePeer(peerId, peer);
      }
    }
  }

  private createPeerConnection(remotePlayerId: string): PeerConnectionWrapper {
    const isPolite = this.localPlayerId! < remotePlayerId;
    const pc = new RTCPeerConnection({
      iceServers: this.getIceServers(),
    });

    const audioElement = new Audio();
    audioElement.autoplay = true;
    (audioElement as any).playsInline = true;
    audioElement.muted = voiceStore.getState().isDeafened;
    const vol = voiceStore.getState().peerVolumes[remotePlayerId] ?? 1.0;
    audioElement.volume = vol;
    if (audioElement.style) {
      audioElement.style.display = "none";
    }
    if (
      typeof Node !== "undefined" &&
      audioElement instanceof Node &&
      typeof document !== "undefined" &&
      document.body &&
      typeof document.body.appendChild === "function"
    ) {
      document.body.appendChild(audioElement);
    }

    const peerWrapper: PeerConnectionWrapper = {
      playerId: remotePlayerId,
      pc,
      isPolite,
      makingOffer: false,
      ignoreOffer: false,
      audioElement,
      pendingCandidates: [],
    };
    this.peers.set(remotePlayerId, peerWrapper);

    // If local microphone stream is already active, add track; otherwise add audio transceiver
    if (this.localStream) {
      const track = this.localStream.getAudioTracks()[0];
      if (track) {
        pc.addTrack(track, this.localStream);
      }
    } else {
      try {
        pc.addTransceiver("audio", { direction: "sendrecv" });
      } catch {
        // Fallback for browsers or testing environments lacking addTransceiver
      }
    }

    // Perfect Negotiation pattern
    pc.onnegotiationneeded = async () => {
      try {
        peerWrapper.makingOffer = true;
        await pc.setLocalDescription();
        if (pc.localDescription) {
          this.sendSignal(remotePlayerId, {
            type: "description",
            sdp: pc.localDescription,
          });
        }
      } catch (err) {
        console.warn(`[WebRTC] Negotiation error with ${remotePlayerId}:`, err);
      } finally {
        peerWrapper.makingOffer = false;
      }
    };

    pc.onicecandidate = ({ candidate }) => {
      if (candidate) {
        this.sendSignal(remotePlayerId, {
          type: "candidate",
          candidate: candidate.toJSON(),
        });
      }
    };

    pc.ontrack = (event) => {
      console.log(
        `[WebRTC] Received remote track from ${remotePlayerId}: kind=${event.track?.kind}, streamCount=${event.streams?.length || 0}`,
      );
      const stream =
        event.streams && event.streams[0]
          ? event.streams[0]
          : new MediaStream([event.track]);
      audioElement.srcObject = stream;
      audioElement.play().catch((err) => {
        console.warn(
          `[WebRTC] Autoplay play() rejected for ${remotePlayerId}:`,
          err,
        );
      });
    };

    pc.onconnectionstatechange = () => {
      console.log(
        `[WebRTC] Peer ${remotePlayerId} connectionState: ${pc.connectionState}`,
      );
    };

    pc.oniceconnectionstatechange = () => {
      console.log(
        `[WebRTC] Peer ${remotePlayerId} iceConnectionState: ${pc.iceConnectionState}`,
      );
    };

    return peerWrapper;
  }

  /**
   * Handle incoming WebRTC signal routed by Gateway via WebSocket
   */
  public async handleSignal(senderPlayerId: string, signal: WebRTCSignalData) {
    if (!senderPlayerId || senderPlayerId === this.localPlayerId) {
      return;
    }

    let peer = this.peers.get(senderPlayerId);
    if (!peer) {
      peer = this.createPeerConnection(senderPlayerId);
    }

    const { pc, isPolite } = peer;

    try {
      if (signal.type === "description" && signal.sdp) {
        const description = signal.sdp;
        const offerCollision =
          description.type === "offer" &&
          (peer.makingOffer || pc.signalingState !== "stable");

        peer.ignoreOffer = !isPolite && offerCollision;
        if (peer.ignoreOffer) {
          console.log(
            `[WebRTC] Impolite peer ignored collision offer from ${senderPlayerId}`,
          );
          return;
        }

        await pc.setRemoteDescription(description);

        // Drain buffered candidates
        if (peer.pendingCandidates.length > 0) {
          console.log(
            `[WebRTC] Draining ${peer.pendingCandidates.length} queued ICE candidates for ${senderPlayerId}`,
          );
          for (const cand of peer.pendingCandidates) {
            try {
              await pc.addIceCandidate(cand);
            } catch (err) {
              console.warn(`[WebRTC] Error adding buffered candidate:`, err);
            }
          }
          peer.pendingCandidates = [];
        }

        if (description.type === "offer") {
          await pc.setLocalDescription();
          if (pc.localDescription) {
            this.sendSignal(senderPlayerId, {
              type: "description",
              sdp: pc.localDescription,
            });
          }
        }
      } else if (signal.type === "candidate" && signal.candidate) {
        if (!pc.remoteDescription || !pc.remoteDescription.type) {
          peer.pendingCandidates.push(signal.candidate);
        } else {
          try {
            await pc.addIceCandidate(signal.candidate);
          } catch (err) {
            if (!peer.ignoreOffer) {
              console.warn(
                `[WebRTC] Error adding candidate from ${senderPlayerId}:`,
                err,
              );
            }
          }
        }
      }
    } catch (err) {
      console.warn(`[WebRTC] Error handling WebRTC signal from ${senderPlayerId}:`, err);
    }
  }

  private sendSignal(targetPlayerId: string, signal: WebRTCSignalData) {
    if (!this.roomId) return;
    wsClient.sendRaw(
      JSON.stringify({
        type: MessageType.VOICE_SIGNAL,
        targetPlayerId,
        signal,
      }),
    );
  }

  private sendVoiceStateUpdate(patch: {
    isMuted?: boolean;
    isDeafened?: boolean;
    isSpeaking?: boolean;
  }) {
    if (!this.roomId) return;
    wsClient.sendRaw(
      JSON.stringify({
        type: MessageType.VOICE_STATE_UPDATE,
        ...patch,
      }),
    );
  }

  private setupVAD(stream: MediaStream) {
    try {
      const AudioCtx =
        window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioCtx) return;

      this.audioContext = new AudioCtx();
      const source = this.audioContext.createMediaStreamSource(stream);
      const analyser = this.audioContext.createAnalyser();
      analyser.fftSize = 256;
      source.connect(analyser);

      const buffer = new Uint8Array(analyser.frequencyBinCount);
      const threshold = 18; // volume amplitude threshold (0-255)

      this.vadInterval = setInterval(() => {
        if (!voiceStore.getState().isMicOn || voiceStore.getState().isMuted) {
          if (this.isSpeaking) {
            this.isSpeaking = false;
            if (this.localPlayerId) {
              voiceStore.setLocalSpeaking(false, this.localPlayerId);
            }
            this.sendVoiceStateUpdate({ isSpeaking: false });
          }
          return;
        }

        analyser.getByteFrequencyData(buffer);
        let sum = 0;
        for (let i = 0; i < buffer.length; i++) {
          sum += buffer[i];
        }
        const avg = sum / buffer.length;
        const now = Date.now();

        if (avg > threshold) {
          if (!this.isSpeaking && now - this.lastSpeakingChangeTime > 150) {
            this.isSpeaking = true;
            this.lastSpeakingChangeTime = now;
            if (this.localPlayerId) {
              voiceStore.setLocalSpeaking(true, this.localPlayerId);
            }
            this.sendVoiceStateUpdate({ isSpeaking: true });
          }
        } else {
          if (this.isSpeaking && now - this.lastSpeakingChangeTime > 400) {
            this.isSpeaking = false;
            this.lastSpeakingChangeTime = now;
            if (this.localPlayerId) {
              voiceStore.setLocalSpeaking(false, this.localPlayerId);
            }
            this.sendVoiceStateUpdate({ isSpeaking: false });
          }
        }
      }, 100);
    } catch (e) {
      console.warn("VAD setup skipped:", e);
    }
  }

  private closePeer(playerId: string, peer: PeerConnectionWrapper) {
    try {
      peer.pc.close();
      peer.audioElement.pause();
      peer.audioElement.srcObject = null;
      if (peer.audioElement.parentNode) {
        peer.audioElement.parentNode.removeChild(peer.audioElement);
      }
    } catch {}
    this.peers.delete(playerId);
    voiceStore.removePeer(playerId);
  }

  public cleanup() {
    this.stopMic();
    for (const [peerId, peer] of this.peers.entries()) {
      this.closePeer(peerId, peer);
    }
    this.peers.clear();
    this.localPlayerId = null;
    this.roomId = null;
    voiceStore.reset();
  }
}

export const voiceChatManager = VoiceChatManager.getInstance();
