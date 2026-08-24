import { useSyncExternalStore } from 'react';

export interface GuessEntry {
  id: string;
  roomId?: string;
  playerId: string;
  username: string;
  guess: string;
  isCorrect?: boolean;
  timestamp: number;
}

interface GuessStoreState {
  guesses: GuessEntry[];
}

let state: GuessStoreState = {
  guesses: [],
};

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

export const guessStore = {
  getState: () => state,
  addGuess: (entry: GuessEntry) => {
    state = {
      guesses: [...state.guesses, entry],
    };
    notify();
  },
  setGuesses: (guesses: GuessEntry[]) => {
    state = { guesses };
    notify();
  },
  clearGuesses: () => {
    state = { guesses: [] };
    notify();
  },
  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useGuessStore<T>(selector: (state: GuessStoreState) => T): T {
  return useSyncExternalStore(
    guessStore.subscribe,
    () => selector(guessStore.getState()),
    () => selector(guessStore.getState())
  );
}
