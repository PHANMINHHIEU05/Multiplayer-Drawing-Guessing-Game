import { describe, it, expect, beforeEach } from 'vitest';
import { metricsStore, calculateP95, calculateJitter } from './metricsStore';

describe('metricsStore & Calculations (TV4)', () => {
  beforeEach(() => {
    metricsStore.reset();
  });

  describe('calculateP95', () => {
    it('returns 0 for empty array', () => {
      expect(calculateP95([])).toBe(0);
    });

    it('calculates p95 correctly for 100 uniform samples 1..100', () => {
      const samples = Array.from({ length: 100 }, (_, i) => i + 1);
      // 95th element in 1..100 is 95
      expect(calculateP95(samples)).toBe(95);
    });

    it('handles skewed outlier distributions', () => {
      const samples = [10, 10, 10, 10, 10, 10, 10, 10, 10, 200];
      const p95 = calculateP95(samples);
      expect(p95).toBe(200);
    });
  });

  describe('calculateJitter', () => {
    it('returns 0 for samples with length < 2', () => {
      expect(calculateJitter([10])).toBe(0);
    });

    it('calculates mean absolute difference accurately', () => {
      // differences: |20-10|=10, |15-20|=5, |25-15|=10 -> mean = 25/3 = 8.3
      const samples = [10, 20, 15, 25];
      expect(calculateJitter(samples)).toBe(8.3);
    });

    it('returns 0 for constant latency without jitter', () => {
      const samples = [20, 20, 20, 20];
      expect(calculateJitter(samples)).toBe(0);
    });
  });

  describe('Heartbeat & Sequence Gap Tracking', () => {
    it('records heartbeat pong and computes RTT & stats', () => {
      const pingSent = Date.now() - 30; // 30ms ago
      metricsStore.recordHeartbeatPong(pingSent, Date.now(), 5, 'gateway-test');

      const state = metricsStore.getState();
      expect(state.rttCurrent).toBeGreaterThanOrEqual(25);
      expect(state.gatewayId).toBe('gateway-test');
      expect(state.gatewayQueueSize).toBe(5);
    });

    it('detects sequence gaps accurately when packets are dropped', () => {
      const strokeId = 'stroke-test-123';

      // Batch 1: seq 0..4 (5 points) -> expected next = 5
      metricsStore.recordDrawBatchReceived(5, strokeId, 0);
      expect(metricsStore.getState().sequenceGapCount).toBe(0);

      // Batch 2: seq 8..11 (4 points) -> gap of 3 (8 - 5 = 3 missing points)
      metricsStore.recordDrawBatchReceived(4, strokeId, 8);
      expect(metricsStore.getState().sequenceGapCount).toBe(3);

      // Batch 3: seq 12..14 (3 points) -> expected 12, no new gap
      metricsStore.recordDrawBatchReceived(3, strokeId, 12);
      expect(metricsStore.getState().sequenceGapCount).toBe(3);
    });

    it('allows changing drawing mode', () => {
      expect(metricsStore.getState().drawingMode).toBe('BINARY_BATCH');
      metricsStore.setDrawingMode('JSON_POINT');
      expect(metricsStore.getState().drawingMode).toBe('JSON_POINT');
    });
  });
});
