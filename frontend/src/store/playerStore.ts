import { useSyncExternalStore } from 'react';

const STORAGE_KEY_ID = 'app_player_id';
const STORAGE_KEY_NAME = 'app_username';

function safeGetItem(key: string): string | null {
  if (typeof window !== 'undefined' && window.localStorage) {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }
  return null;
}

function safeSetItem(key: string, value: string): void {
  if (typeof window !== 'undefined' && window.localStorage) {
    try {
      localStorage.setItem(key, value);
    } catch {
      // Ignore
    }
  }
}

function safeRemoveItem(key: string): void {
  if (typeof window !== 'undefined' && window.localStorage) {
    try {
      localStorage.removeItem(key);
    } catch {
      // Ignore
    }
  }
}

function getInitialPlayerId(): string {
  const saved = safeGetItem(STORAGE_KEY_ID);
  if (saved) return saved;
  const newId = 'player_' + Math.random().toString(36).substring(2, 9);
  safeSetItem(STORAGE_KEY_ID, newId);
  return newId;
}

function getInitialUsername(): string {
  return safeGetItem(STORAGE_KEY_NAME) || '';
}

interface PlayerState {
  playerId: string;
  username: string;
}

let state: PlayerState = {
  playerId: getInitialPlayerId(),
  username: getInitialUsername(),
};

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

export const playerStore = {
  getState: () => state,
  setPlayer: (username: string, playerId?: string) => {
    const pId = playerId || state.playerId;
    safeSetItem(STORAGE_KEY_ID, pId);
    safeSetItem(STORAGE_KEY_NAME, username);
    state = { playerId: pId, username };
    notify();
  },
  clearPlayer: () => {
    safeRemoveItem(STORAGE_KEY_NAME);
    state = { ...state, username: '' };
    notify();
  },
  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function usePlayerStore<T>(selector: (state: PlayerState) => T): T {
  return useSyncExternalStore(
    playerStore.subscribe,
    () => selector(playerStore.getState()),
    () => selector(playerStore.getState())
  );
}
