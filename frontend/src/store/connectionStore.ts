import { useSyncExternalStore } from 'react';

export type ConnectionStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING';

interface ConnectionState {
  status: ConnectionStatus;
  lastError: string | null;
}

let state: ConnectionState = {
  status: 'DISCONNECTED',
  lastError: null,
};

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

export const connectionStore = {
  getState: () => state,
  setStatus: (status: ConnectionStatus) => {
    state = { ...state, status, lastError: status === 'CONNECTED' ? null : state.lastError };
    notify();
  },
  setLastError: (error: string | null) => {
    state = { ...state, lastError: error };
    notify();
  },
  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useConnectionStore<T>(selector: (state: ConnectionState) => T): T {
  return useSyncExternalStore(
    connectionStore.subscribe,
    () => selector(connectionStore.getState()),
    () => selector(connectionStore.getState())
  );
}
