import { useSyncExternalStore } from 'react';
import { DrawPoint } from '../types/game';

/**
 * TV7 — Canvas recovery coordination state.
 *
 * During recovery (between GET_CANVAS_STATE being sent and SYNC_CANVAS_STATE
 * being applied), incoming live drawing frames are BUFFERED instead of applied,
 * then flushed after the recovered history. This closes the recovery/live race:
 * no missing stroke, no duplicate stroke, deterministic order.
 *
 * Dedup safety: recovered events and buffered live events are tagged with the
 * recovery generation. A late SYNC_CANVAS_STATE from an older generation is
 * ignored (see WebSocketClient request correlation via requestId).
 */
export type CanvasMode = 'LIVE' | 'RECOVERING';

interface RecoveryState {
  mode: CanvasMode;
  /** Recovery generation — incremented on every new recovery attempt. */
  generation: number;
  /** Live frames buffered while RECOVERING (raw decoded payloads, not yet applied). */
  buffer: DrawPoint[];
  /** Round the current recovery is for. */
  round: number | null;
}

let state: RecoveryState = {
  mode: 'LIVE',
  generation: 0,
  buffer: [],
  round: null,
};

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

export const recoveryStore = {
  getState: () => state,

  /** Begin a recovery attempt: live frames will be buffered until complete. */
  begin: (round: number) => {
    state = { mode: 'RECOVERING', generation: state.generation + 1, buffer: [], round };
    notify();
  },

  /** Buffer a live frame while RECOVERING (caller checks mode first). */
  buffer: (points: DrawPoint[]) => {
    if (state.mode !== 'RECOVERING') return;
    state = { ...state, buffer: [...state.buffer, ...points] };
  },

  /** Clear buffered frames on canvas-clear during recovery. */
  clearBuffer: () => {
    state = { ...state, buffer: [] };
  },

  /** Complete recovery: returns buffered frames to flush, returns to LIVE mode. */
  complete: (): DrawPoint[] => {
    const buffered = state.buffer;
    state = { mode: 'LIVE', generation: state.generation, buffer: [], round: null };
    notify();
    return buffered;
  },

  /** Abort recovery (e.g. recovery failed or round changed) — drop buffer. */
  abort: () => {
    state = { mode: 'LIVE', generation: state.generation, buffer: [], round: null };
    notify();
  },

  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useRecoveryStore<T>(selector: (state: RecoveryState) => T): T {
  return useSyncExternalStore(
    recoveryStore.subscribe,
    () => selector(recoveryStore.getState()),
    () => selector(recoveryStore.getState())
  );
}
