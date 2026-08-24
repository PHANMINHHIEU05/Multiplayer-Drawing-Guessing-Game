import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { PointBatcher } from './usePointBatcher';
import { DrawPoint } from '../../types/game';

describe('PointBatcher', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  const createPoint = (x: number, y: number, isNewPath = false): DrawPoint => ({
    x,
    y,
    color: '#f8fafc',
    size: 4,
    isNewPath,
    timestamp: Date.now(),
  });

  it('immediately flushes starting points (isNewPath = true)', () => {
    const onFlush = vi.fn();
    const batcher = new PointBatcher({ onFlush, batchIntervalMs: 16 });

    const startPoint = createPoint(0.1, 0.2, true);
    batcher.addPoint(startPoint);

    expect(onFlush).toHaveBeenCalledTimes(1);
    expect(onFlush).toHaveBeenCalledWith([startPoint]);
  });

  it('batches move points and flushes after 16ms', () => {
    const onFlush = vi.fn();
    const batcher = new PointBatcher({ onFlush, batchIntervalMs: 16 });

    const move1 = createPoint(0.2, 0.3, false);
    const move2 = createPoint(0.3, 0.4, false);
    const move3 = createPoint(0.4, 0.5, false);

    batcher.addPoint(move1);
    batcher.addPoint(move2);
    batcher.addPoint(move3);

    // Before 16ms elapsed, not flushed yet
    expect(onFlush).not.toHaveBeenCalled();
    expect(batcher.getPendingCount()).toBe(3);

    // Advance 16ms
    vi.advanceTimersByTime(16);

    expect(onFlush).toHaveBeenCalledTimes(1);
    expect(onFlush).toHaveBeenCalledWith([move1, move2, move3]);
    expect(batcher.getPendingCount()).toBe(0);
  });

  it('supports explicit flush()', () => {
    const onFlush = vi.fn();
    const batcher = new PointBatcher({ onFlush, batchIntervalMs: 16 });

    const move1 = createPoint(0.1, 0.1, false);
    batcher.addPoint(move1);

    batcher.flush();

    expect(onFlush).toHaveBeenCalledTimes(1);
    expect(onFlush).toHaveBeenCalledWith([move1]);
    expect(batcher.getPendingCount()).toBe(0);
  });

  it('supports clear() without flushing', () => {
    const onFlush = vi.fn();
    const batcher = new PointBatcher({ onFlush, batchIntervalMs: 16 });

    const move1 = createPoint(0.1, 0.1, false);
    batcher.addPoint(move1);
    batcher.clear();

    vi.advanceTimersByTime(20);

    expect(onFlush).not.toHaveBeenCalled();
    expect(batcher.getPendingCount()).toBe(0);
  });
});
