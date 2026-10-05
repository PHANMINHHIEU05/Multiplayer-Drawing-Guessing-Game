import { useSyncExternalStore } from "react";

export type MicPermissionStatus = "prompt" | "granted" | "denied" | "unsupported";

export interface PeerVoiceState {
  isMuted: boolean;
  isDeafened: boolean;
  isSpeaking: boolean;
}

export interface VoiceState {
  isVoiceRoomEnabled: boolean;
  isMicOn: boolean;
  isMuted: boolean;
  isDeafened: boolean;
  isLocalSpeaking: boolean;
  speakingPlayers: Record<string, boolean>;
  peerVoiceStates: Record<string, PeerVoiceState>;
  peerVolumes: Record<string, number>;
  micPermission: MicPermissionStatus;
  micError: string | null;
  audioDevices: MediaDeviceInfo[];
  selectedDeviceId: string | null;
}

const initialState: VoiceState = {
  isVoiceRoomEnabled: true,
  isMicOn: false,
  isMuted: false,
  isDeafened: false,
  isLocalSpeaking: false,
  speakingPlayers: {},
  peerVoiceStates: {},
  peerVolumes: {},
  micPermission: "prompt",
  micError: null,
  audioDevices: [],
  selectedDeviceId: null,
};

let state: VoiceState = { ...initialState };
const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

export const voiceStore = {
  getState: () => state,

  setVoiceRoomEnabled: (enabled: boolean) => {
    state = { ...state, isVoiceRoomEnabled: enabled };
    notify();
  },

  setMicOn: (on: boolean) => {
    state = { ...state, isMicOn: on };
    notify();
  },

  setMuted: (muted: boolean) => {
    state = { ...state, isMuted: muted };
    notify();
  },

  setDeafened: (deafened: boolean) => {
    state = { ...state, isDeafened: deafened };
    notify();
  },

  setLocalSpeaking: (speaking: boolean, localPlayerId?: string) => {
    const updatedSpeaking = { ...state.speakingPlayers };
    if (localPlayerId) {
      if (speaking) {
        updatedSpeaking[localPlayerId] = true;
      } else {
        delete updatedSpeaking[localPlayerId];
      }
    }
    state = {
      ...state,
      isLocalSpeaking: speaking,
      speakingPlayers: updatedSpeaking,
    };
    notify();
  },

  setPlayerSpeaking: (playerId: string, speaking: boolean) => {
    const updatedSpeaking = { ...state.speakingPlayers };
    if (speaking) {
      updatedSpeaking[playerId] = true;
    } else {
      delete updatedSpeaking[playerId];
    }

    const peerState = state.peerVoiceStates[playerId] || {
      isMuted: false,
      isDeafened: false,
      isSpeaking: false,
    };

    state = {
      ...state,
      speakingPlayers: updatedSpeaking,
      peerVoiceStates: {
        ...state.peerVoiceStates,
        [playerId]: { ...peerState, isSpeaking: speaking },
      },
    };
    notify();
  },

  setPeerVoiceState: (playerId: string, peerState: Partial<PeerVoiceState>) => {
    const existing = state.peerVoiceStates[playerId] || {
      isMuted: false,
      isDeafened: false,
      isSpeaking: false,
    };
    const updated = { ...existing, ...peerState };

    const updatedSpeaking = { ...state.speakingPlayers };
    if (updated.isSpeaking && !updated.isMuted) {
      updatedSpeaking[playerId] = true;
    } else {
      delete updatedSpeaking[playerId];
    }

    state = {
      ...state,
      speakingPlayers: updatedSpeaking,
      peerVoiceStates: {
        ...state.peerVoiceStates,
        [playerId]: updated,
      },
    };
    notify();
  },

  setPeerVolume: (playerId: string, volume: number) => {
    const clamped = Math.max(0, Math.min(1, volume));
    state = {
      ...state,
      peerVolumes: {
        ...state.peerVolumes,
        [playerId]: clamped,
      },
    };
    notify();
  },

  setMicPermission: (status: MicPermissionStatus) => {
    state = { ...state, micPermission: status };
    notify();
  },

  setMicError: (error: string | null) => {
    state = { ...state, micError: error };
    notify();
  },

  setAudioDevices: (devices: MediaDeviceInfo[]) => {
    state = { ...state, audioDevices: devices };
    notify();
  },

  setSelectedDeviceId: (deviceId: string | null) => {
    state = { ...state, selectedDeviceId: deviceId };
    notify();
  },

  removePeer: (playerId: string) => {
    const updatedSpeaking = { ...state.speakingPlayers };
    delete updatedSpeaking[playerId];

    const updatedPeers = { ...state.peerVoiceStates };
    delete updatedPeers[playerId];

    const updatedVolumes = { ...state.peerVolumes };
    delete updatedVolumes[playerId];

    state = {
      ...state,
      speakingPlayers: updatedSpeaking,
      peerVoiceStates: updatedPeers,
      peerVolumes: updatedVolumes,
    };
    notify();
  },

  reset: () => {
    state = { ...initialState };
    notify();
  },

  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useVoiceStore<T>(selector: (state: VoiceState) => T): T {
  return useSyncExternalStore(
    voiceStore.subscribe,
    () => selector(voiceStore.getState()),
    () => selector(voiceStore.getState())
  );
}
