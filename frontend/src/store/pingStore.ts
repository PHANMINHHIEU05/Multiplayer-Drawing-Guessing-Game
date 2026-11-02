import { useSyncExternalStore } from "react";

export interface CanvasPing {
  id: string;
  playerId: string;
  displayName: string;
  gameId: string;
  roundNumber: number;
  /** Ping flavour id; the double-click ping always sends "MISSING". */
  pingType: string;
  /** Normalized canvas coordinate (0.0 - 1.0), same space as DrawPoint. */
  x: number;
  y: number;
}

const MAX_PINGS = 12;
const PING_TTL_MS = 2500;

let pings: CanvasPing[] = [];
const listeners = new Set<() => void>();
const notify = () => listeners.forEach((listener) => listener());

export const pingStore = {
  getState: () => pings,
  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  add: (ping: Omit<CanvasPing, "id">) => {
    const item: CanvasPing = {
      ...ping,
      id: `${Date.now()}-${Math.random()}`,
    };
    pings = [...pings.slice(-(MAX_PINGS - 1)), item];
    notify();
    window.setTimeout(() => {
      pings = pings.filter((current) => current.id !== item.id);
      notify();
    }, PING_TTL_MS);
  },
  clear: () => {
    if (pings.length) {
      pings = [];
      notify();
    }
  },
};

export function usePings(): CanvasPing[] {
  return useSyncExternalStore(pingStore.subscribe, pingStore.getState, pingStore.getState);
}
