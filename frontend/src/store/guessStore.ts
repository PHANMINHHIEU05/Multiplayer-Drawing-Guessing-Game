import { useSyncExternalStore } from 'react';

/**
 * Server-authoritative guess outcome for the submitting player.
 * Mirrors the private GUESS_RESULT event returned by the realtime gateway.
 */
export type GuessResultStatus =
  | 'CORRECT'
  | 'CLOSE'
  | 'WRONG'
  | 'ALREADY_GUESSED'
  | 'TIME_EXPIRED'
  | 'ROUND_NOT_ACTIVE'
  | 'RATE_LIMITED'
  | 'ERROR';

export interface GuessEntry {
  id: string;
  roomId?: string;
  playerId: string;
  username: string;
  guess: string;
  isCorrect?: boolean;
  result?: GuessResultStatus;
  scoreDelta?: number;
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
  /** Attach the private guess result (from the server) to a locally-submitted guess entry. */
  updateGuess: (id: string, patch: Partial<Pick<GuessEntry, 'result' | 'scoreDelta' | 'isCorrect'>>) => {
    state = {
      guesses: state.guesses.map((g) => (g.id === id ? { ...g, ...patch } : g)),
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
