import { useSyncExternalStore } from "react";

export interface FloatingReaction {
  id: string;
  playerId: string;
  displayName: string;
  reactionType: string;
  gameId: string;
  roundNumber: number;
  x: number;
  y: number;
}

let reactions: FloatingReaction[] = [];
const listeners = new Set<() => void>();
const notify = () => listeners.forEach((listener) => listener());

export const reactionStore = {
  getState: () => reactions,
  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  add: (reaction: Omit<FloatingReaction, "id" | "x" | "y">) => {
    const item: FloatingReaction = {
      ...reaction,
      id: `${Date.now()}-${Math.random()}`,
      x: 18 + Math.random() * 64,
      y: 22 + Math.random() * 48,
    };
    reactions = [...reactions.slice(-11), item];
    notify();
    window.setTimeout(() => {
      reactions = reactions.filter((current) => current.id !== item.id);
      notify();
    }, 1800);
  },
  clear: () => {
    if (reactions.length) {
      reactions = [];
      notify();
    }
  },
};

export function useReactions(): FloatingReaction[] {
  return useSyncExternalStore(reactionStore.subscribe, reactionStore.getState, reactionStore.getState);
}
