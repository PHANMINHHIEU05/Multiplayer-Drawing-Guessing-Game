import { useSyncExternalStore } from "react";

export type ConnectionStatus =
  | "DISCONNECTED"
  | "CONNECTING"
  | "CONNECTED"
  | "RECONNECTING"
  | "FAILING_OVER"
  | "RECOVERING";

interface ConnectionState {
  status: ConnectionStatus;
  lastError: string | null;
}

let state: ConnectionState = {
  status: "DISCONNECTED",
  lastError: null,
};

const listeners = new Set<() => void>();
let lastErrorTimer: ReturnType<typeof setTimeout> | null = null;

function notify() {
  listeners.forEach((l) => l());
}

export const connectionStore = {
  getState: () => state,
  setStatus: (status: ConnectionStatus) => {
    if (status === "CONNECTED" && lastErrorTimer) {
      clearTimeout(lastErrorTimer);
      lastErrorTimer = null;
    }
    state = {
      ...state,
      status,
      lastError: status === "CONNECTED" ? null : state.lastError,
    };
    notify();
  },
  setLastError: (error: string | null) => {
    if (lastErrorTimer) {
      clearTimeout(lastErrorTimer);
      lastErrorTimer = null;
    }
    state = { ...state, lastError: error };
    notify();
    if (error) {
      lastErrorTimer = setTimeout(() => {
        lastErrorTimer = null;
        state = { ...state, lastError: null };
        notify();
      }, 6000);
    }
  },
  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useConnectionStore<T>(
  selector: (state: ConnectionState) => T,
): T {
  return useSyncExternalStore(
    connectionStore.subscribe,
    () => selector(connectionStore.getState()),
    () => selector(connectionStore.getState()),
  );
}
