import { useSyncExternalStore } from 'react';
import { Room, Player } from '../types/room';

interface RoomState {
  room: Room | null;
  isInRoom: boolean;
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
  setRoom: (room: Room | null) => {
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
  clearRoom: () => {
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
