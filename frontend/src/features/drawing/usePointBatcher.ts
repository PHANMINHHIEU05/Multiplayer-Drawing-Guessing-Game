import { useRef, useEffect, useCallback } from 'react';
import { DrawPoint } from '../../types/game';

export interface PointBatcherOptions {
  /** Callback fired when a batch of points is ready to be sent to the network */
  onFlush: (points: DrawPoint[]) => void;
  /** Batch interval in ms (default: 16ms ~ 60 FPS) */
  batchIntervalMs?: number;
}

/**
 * Core Point Batcher logic (pure TypeScript, framework-agnostic)
 */
export class PointBatcher {
  private buffer: DrawPoint[] = [];
  private timer: any = null;
  private onFlush: (points: DrawPoint[]) => void;
  private batchIntervalMs: number;

  constructor(options: PointBatcherOptions) {
    this.onFlush = options.onFlush;
    this.batchIntervalMs = options.batchIntervalMs ?? 16;
  }

  public updateOnFlush(onFlush: (points: DrawPoint[]) => void): void {
    this.onFlush = onFlush;
  }

  public addPoint(point: DrawPoint): void {
    if (point.isNewPath) {
      // Flush previous stroke segment immediately
      this.flush();
      // Send new path start point immediately with zero delay
      this.onFlush([point]);
      return;
    }

    // Accumulate move points in the buffer
    this.buffer.push(point);

    if (this.timer === null) {
      this.timer = setTimeout(() => {
        this.timer = null;
        this.flush();
      }, this.batchIntervalMs);
    }
  }

  public flush(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }

    if (this.buffer.length === 0) return;

    const pointsToSend = this.buffer;
    this.buffer = [];
    this.onFlush(pointsToSend);
  }

  public clear(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.buffer = [];
  }

  public getPendingCount(): number {
    return this.buffer.length;
  }

  public destroy(): void {
    this.clear();
  }
}

/**
 * React hook wrapper around PointBatcher
 */
export function usePointBatcher(options: PointBatcherOptions) {
  const batcherRef = useRef<PointBatcher | null>(null);

  if (!batcherRef.current) {
    batcherRef.current = new PointBatcher(options);
  }

  // Keep callback updated
  useEffect(() => {
    batcherRef.current?.updateOnFlush(options.onFlush);
  }, [options.onFlush]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      batcherRef.current?.destroy();
    };
  }, []);

  const addPoint = useCallback((point: DrawPoint) => {
    batcherRef.current?.addPoint(point);
  }, []);

  const flush = useCallback(() => {
    batcherRef.current?.flush();
  }, []);

  const clear = useCallback(() => {
    batcherRef.current?.clear();
  }, []);

  const getPendingCount = useCallback(() => {
    return batcherRef.current?.getPendingCount() ?? 0;
  }, []);

  return {
    addPoint,
    flush,
    clear,
    cancelActiveStroke: clear,
    getPendingCount,
  };
}
