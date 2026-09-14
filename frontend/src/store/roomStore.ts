import { useSyncExternalStore } from 'react';
import { Room, Player } from '../types/room';

interface RoomState {
  room: Room | null;
  isInRoom: boolean;
}

const STORAGE_KEY_ROOM = 'app_last_room_id';

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

let state: RoomState = {
  room: null,
  isInRoom: false,
};

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

export const roomStore = {
  getState: () => state,
  /** TV7: last joined roomId — used to auto-RESUME after a page refresh. */
  getLastRoomId: (): string | null => safeGetItem(STORAGE_KEY_ROOM),
  setRoom: (room: Room | null) => {
    if (room) {
      safeSetItem(STORAGE_KEY_ROOM, room.roomId);
    } else {
      safeRemoveItem(STORAGE_KEY_ROOM);
    }
    state = {
      room,
      isInRoom: !!room,
    };
    notify();
  },
  updatePlayers: (players: Player[]) => {
    if (state.room) {
      state = {
        ...state,
        room: {
          ...state.room,
          players,
          playerCount: players.length,
        },
      };
      notify();
    }
  },
  /** TV7: explicit leave — clears resume metadata so reconnect cannot silently rejoin. */
  clearRoom: () => {
    safeRemoveItem(STORAGE_KEY_ROOM);
    state = { room: null, isInRoom: false };
    notify();
  },
  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useRoomStore<T>(selector: (state: RoomState) => T): T {
  return useSyncExternalStore(
    roomStore.subscribe,
    () => selector(roomStore.getState()),
    () => selector(roomStore.getState())
  );
}
