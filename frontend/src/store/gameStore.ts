import { useSyncExternalStore } from 'react';
import { GameState, DrawPoint } from '../types/game';

interface GameStoreState {
  gameState: GameState | null;
  drawPoints: DrawPoint[];
  currentDrawerId: string | null;
  secretWord: string | null;
  hint: string | null;
}

let state: GameStoreState = {
  gameState: null,
  drawPoints: [],
  currentDrawerId: null,
  secretWord: null,
  hint: null,
};

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

export const gameStore = {
  getState: () => state,
  setGameState: (gameState: GameState | null) => {
    state = {
      ...state,
      gameState,
      currentDrawerId: gameState?.drawerId || null,
      secretWord: gameState?.secretWord || null,
      hint: gameState?.hint || null,
    };
    notify();
  },
  addDrawPoint: (point: DrawPoint) => {
    state = {
      ...state,
      drawPoints: [...state.drawPoints, point],
    };
    notify();
  },
  clearDrawPoints: () => {
    state = { ...state, drawPoints: [] };
    notify();
  },
  setDrawPoints: (points: DrawPoint[]) => {
    state = { ...state, drawPoints: points };
    notify();
  },
  clearGame: () => {
    state = {
      gameState: null,
      drawPoints: [],
      currentDrawerId: null,
      secretWord: null,
      hint: null,
    };
    notify();
  },
  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useGameStore<T>(selector: (state: GameStoreState) => T): T {
  return useSyncExternalStore(
    gameStore.subscribe,
    () => selector(gameStore.getState()),
    () => selector(gameStore.getState())
  );
}
