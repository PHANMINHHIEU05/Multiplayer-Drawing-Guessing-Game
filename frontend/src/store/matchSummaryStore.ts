import { useSyncExternalStore } from "react";
import { PlayerScore, MatchAward } from "../types/game";

export interface MatchSummary {
  roomId: string;
  gameId?: string;
  scores: PlayerScore[];
  awards: MatchAward[];
  winner?: { playerId: string; username: string; score: number };
}

interface MatchSummaryState {
  summary: MatchSummary | null;
  isOpen: boolean;
}

let state: MatchSummaryState = {
  summary: null,
  isOpen: false,
};

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

export const matchSummaryStore = {
  getState: () => state,
  setSummary: (summary: MatchSummary) => {
    state = {
      summary,
      isOpen: true,
    };
    notify();
  },
  close: () => {
    state = {
      ...state,
      isOpen: false,
    };
    notify();
  },
  clear: () => {
    state = {
      summary: null,
      isOpen: false,
    };
    notify();
  },
  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useMatchSummaryStore<T>(
  selector: (state: MatchSummaryState) => T,
): T {
  return useSyncExternalStore(
    matchSummaryStore.subscribe,
    () => selector(matchSummaryStore.getState()),
    () => selector(matchSummaryStore.getState()),
  );
}
