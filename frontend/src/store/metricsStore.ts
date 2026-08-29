import { useSyncExternalStore } from 'react';

export type DrawingProtocolMode = 'BINARY_BATCH' | 'JSON_BATCH' | 'JSON_POINT';

export interface MetricsState {
  // Connection
  status: 'CONNECTED' | 'CONNECTING' | 'RECONNECTING' | 'DISCONNECTED';
  reconnectCount: number;
  heartbeatTimeoutCount: number;
  gatewayId: string;
  gatewayQueueSize: number;

  // Latency & Jitter
  rttCurrent: number;
  rttAvg: number;
  rttP95: number;
  jitter: number;
  lastPongReceivedAt: number;

  // Throughput & Bandwidth (Total counters)
  txMessages: number;
  rxMessages: number;
  txBytes: number;
  rxBytes: number;

  // Rates (per second)
  txMsgRate: number;
  rxMsgRate: number;
  txBandwidthBytesPerSec: number;
  rxBandwidthBytesPerSec: number;

  // Drawing Stream Telemetry
  drawingMode: DrawingProtocolMode;
  drawBatchesSent: number;
  drawBatchesReceived: number;
  pointsSent: number;
  pointsReceived: number;
  drawBatchesPerSec: number;
  avgPointsPerBatch: number;
  sequenceGapCount: number;

  // Network Inspector UI State
  isInspectorOpen: boolean;
}

const RTT_WINDOW_SIZE = 200;
let rttSamples: number[] = [];
let strokeSeqMap = new Map<string, number>();

// Rate tracking deltas
let lastTickTime = Date.now();
let lastTxMessages = 0;
let lastRxMessages = 0;
let lastTxBytes = 0;
let lastRxBytes = 0;
let lastDrawBatches = 0;

let state: MetricsState = {
  status: 'DISCONNECTED',
  reconnectCount: 0,
  heartbeatTimeoutCount: 0,
  gatewayId: 'gateway-1',
  gatewayQueueSize: 0,

  rttCurrent: 0,
  rttAvg: 0,
  rttP95: 0,
  jitter: 0,
  lastPongReceivedAt: 0,

  txMessages: 0,
  rxMessages: 0,
  txBytes: 0,
  rxBytes: 0,

  txMsgRate: 0,
  rxMsgRate: 0,
  txBandwidthBytesPerSec: 0,
  rxBandwidthBytesPerSec: 0,

  drawingMode: 'BINARY_BATCH',
  drawBatchesSent: 0,
  drawBatchesReceived: 0,
  pointsSent: 0,
  pointsReceived: 0,
  drawBatchesPerSec: 0,
  avgPointsPerBatch: 0,
  sequenceGapCount: 0,

  isInspectorOpen: true,
};

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

/** Compute p95 percentile from samples using nearest rank */
export function calculateP95(samples: number[]): number {
  if (samples.length === 0) return 0;
  const sorted = [...samples].sort((a, b) => a - b);
  const index = Math.min(Math.round(0.95 * (sorted.length - 1)), sorted.length - 1);
  return Math.round(sorted[index]);
}

/** Compute jitter as mean absolute difference between consecutive RTT samples */
export function calculateJitter(samples: number[]): number {
  if (samples.length < 2) return 0;
  let diffSum = 0;
  for (let i = 1; i < samples.length; i++) {
    diffSum += Math.abs(samples[i] - samples[i - 1]);
  }
  return Math.round((diffSum / (samples.length - 1)) * 10) / 10;
}

export const metricsStore = {
  getState: () => state,

  setStatus: (status: MetricsState['status']) => {
    state = { ...state, status };
    notify();
  },

  setGatewayInfo: (gatewayId?: string, queueSize?: number) => {
    state = {
      ...state,
      gatewayId: gatewayId || state.gatewayId,
      gatewayQueueSize: queueSize !== undefined ? queueSize : state.gatewayQueueSize,
    };
    notify();
  },

  recordHeartbeatPong: (clientTimestamp: number, _serverTimestamp?: number, queueSize?: number, gatewayId?: string) => {
    const now = Date.now();
    const rtt = Math.max(0, now - clientTimestamp);

    rttSamples.push(rtt);
    if (rttSamples.length > RTT_WINDOW_SIZE) {
      rttSamples.shift();
    }

    const sum = rttSamples.reduce((acc, v) => acc + v, 0);
    const avg = Math.round((sum / rttSamples.length) * 10) / 10;
    const p95 = calculateP95(rttSamples);
    const jitter = calculateJitter(rttSamples);

    state = {
      ...state,
      rttCurrent: rtt,
      rttAvg: avg,
      rttP95: p95,
      jitter,
      lastPongReceivedAt: now,
      gatewayQueueSize: queueSize !== undefined ? queueSize : state.gatewayQueueSize,
      gatewayId: gatewayId || state.gatewayId,
    };
    notify();
  },

  recordTx: (byteLength: number) => {
    state = {
      ...state,
      txMessages: state.txMessages + 1,
      txBytes: state.txBytes + byteLength,
    };
    notify();
  },

  recordRx: (byteLength: number) => {
    state = {
      ...state,
      rxMessages: state.rxMessages + 1,
      rxBytes: state.rxBytes + byteLength,
    };
    notify();
  },

  recordDrawBatchSent: (pointCount: number) => {
    const newBatches = state.drawBatchesSent + 1;
    const newPoints = state.pointsSent + pointCount;
    state = {
      ...state,
      drawBatchesSent: newBatches,
      pointsSent: newPoints,
      avgPointsPerBatch: Math.round((newPoints / Math.max(1, newBatches)) * 10) / 10,
    };
    notify();
  },

  recordDrawBatchReceived: (pointCount: number, strokeId?: string, seqStart?: number) => {
    const newBatches = state.drawBatchesReceived + 1;
    const newPoints = state.pointsReceived + pointCount;

    let newGaps = state.sequenceGapCount;
    if (strokeId && seqStart !== undefined) {
      const expected = strokeSeqMap.get(strokeId);
      if (expected !== undefined && seqStart > expected) {
        newGaps += (seqStart - expected);
      }
      strokeSeqMap.set(strokeId, seqStart + pointCount);
    }

    state = {
      ...state,
      drawBatchesReceived: newBatches,
      pointsReceived: newPoints,
      sequenceGapCount: newGaps,
      avgPointsPerBatch: Math.round((newPoints / Math.max(1, newBatches)) * 10) / 10,
    };
    notify();
  },

  resetStrokeSequence: (strokeId?: string) => {
    if (strokeId) {
      strokeSeqMap.delete(strokeId);
    } else {
      strokeSeqMap.clear();
    }
  },

  incrementReconnect: () => {
    state = { ...state, reconnectCount: state.reconnectCount + 1 };
    notify();
  },

  incrementHeartbeatTimeout: () => {
    state = { ...state, heartbeatTimeoutCount: state.heartbeatTimeoutCount + 1 };
    notify();
  },

  setDrawingMode: (drawingMode: DrawingProtocolMode) => {
    state = { ...state, drawingMode };
    notify();
  },

  toggleInspector: () => {
    state = { ...state, isInspectorOpen: !state.isInspectorOpen };
    notify();
  },

  setInspectorOpen: (open: boolean) => {
    state = { ...state, isInspectorOpen: open };
    notify();
  },

  /** Periodic timer calculating per-second rates */
  tickRates: () => {
    const now = Date.now();
    const elapsedSec = Math.max(0.1, (now - lastTickTime) / 1000);

    const txMsgDelta = state.txMessages - lastTxMessages;
    const rxMsgDelta = state.rxMessages - lastRxMessages;
    const txBytesDelta = state.txBytes - lastTxBytes;
    const rxBytesDelta = state.rxBytes - lastRxBytes;
    const drawBatchesDelta = (state.drawBatchesSent + state.drawBatchesReceived) - lastDrawBatches;

    state = {
      ...state,
      txMsgRate: Math.round(txMsgDelta / elapsedSec),
      rxMsgRate: Math.round(rxMsgDelta / elapsedSec),
      txBandwidthBytesPerSec: Math.round(txBytesDelta / elapsedSec),
      rxBandwidthBytesPerSec: Math.round(rxBytesDelta / elapsedSec),
      drawBatchesPerSec: Math.round(drawBatchesDelta / elapsedSec),
    };

    lastTickTime = now;
    lastTxMessages = state.txMessages;
    lastRxMessages = state.rxMessages;
    lastTxBytes = state.txBytes;
    lastRxBytes = state.rxBytes;
    lastDrawBatches = (state.drawBatchesSent + state.drawBatchesReceived);

    notify();
  },

  reset: () => {
    rttSamples = [];
    strokeSeqMap.clear();
    lastTickTime = Date.now();
    lastTxMessages = 0;
    lastRxMessages = 0;
    lastTxBytes = 0;
    lastRxBytes = 0;
    lastDrawBatches = 0;

    state = {
      ...state,
      reconnectCount: 0,
      heartbeatTimeoutCount: 0,
      rttCurrent: 0,
      rttAvg: 0,
      rttP95: 0,
      jitter: 0,
      txMessages: 0,
      rxMessages: 0,
      txBytes: 0,
      rxBytes: 0,
      txMsgRate: 0,
      rxMsgRate: 0,
      txBandwidthBytesPerSec: 0,
      rxBandwidthBytesPerSec: 0,
      drawBatchesSent: 0,
      drawBatchesReceived: 0,
      pointsSent: 0,
      pointsReceived: 0,
      drawBatchesPerSec: 0,
      avgPointsPerBatch: 0,
      sequenceGapCount: 0,
    };
    notify();
  },

  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useMetricsStore<T>(selector: (state: MetricsState) => T): T {
  return useSyncExternalStore(
    metricsStore.subscribe,
    () => selector(metricsStore.getState()),
    () => selector(metricsStore.getState())
  );
}
