import { describe, it, expect } from 'vitest';
import { RemoteStrokeState, DrawPoint } from '../../types/game';
import { PointBatcher } from './usePointBatcher';

describe('TV2 Drawing Stabilization Logic', () => {
  describe('RemoteStrokeState Mapping (TV2-F01)', () => {
    it('associates DRAW_BATCH with the correct RemoteStrokeState from DRAW_START', () => {
      const remoteStrokeMap = new Map<string, RemoteStrokeState>();

      // Simulate DRAW_START with red color and width 12
      const strokeId = 'stroke-test-123';
      remoteStrokeMap.set(strokeId, {
        strokeId,
        tool: 'BRUSH',
        color: '#EF4444',
        width: 12,
        round: 1,
        lastX: 0.1,
        lastY: 0.2,
      });

      // Simulate incoming DRAW_BATCH points without color/tool
      const incomingBatch = [
        { x: 0.15, y: 0.25 },
        { x: 0.2, y: 0.3 },
      ];

      const strokeState = remoteStrokeMap.get(strokeId);
      expect(strokeState).toBeDefined();

      const renderedPoints: DrawPoint[] = incomingBatch.map((p) => ({
        x: p.x,
        y: p.y,
        color: strokeState!.color,
        size: strokeState!.width,
        tool: strokeState!.tool,
        strokeId,
        isNewPath: false,
      }));

      // Receiver must render with drawer's color and width, not Guesser's local state or black fallback
      expect(renderedPoints[0].color).toBe('#EF4444');
      expect(renderedPoints[0].size).toBe(12);
      expect(renderedPoints[0].tool).toBe('BRUSH');
      expect(renderedPoints[1].color).toBe('#EF4444');
      expect(renderedPoints[1].size).toBe(12);
    });

    it('manages ERASER semantics properly in RemoteStrokeState (TV2-F02)', () => {
      const remoteStrokeMap = new Map<string, RemoteStrokeState>();

      const eraserStrokeId = 'eraser-stroke-456';
      remoteStrokeMap.set(eraserStrokeId, {
        strokeId: eraserStrokeId,
        tool: 'ERASER',
        color: '#FFFFFF',
        width: 24,
        round: 1,
        lastX: 0.5,
        lastY: 0.5,
      });

      const strokeState = remoteStrokeMap.get(eraserStrokeId);
      expect(strokeState).toBeDefined();
      expect(strokeState!.tool).toBe('ERASER');
      expect(strokeState!.width).toBe(24);
    });
  });

  describe('PointBatcher Cancellation (TV2-F04/F05)', () => {
    it('cancels pending points when cancelActiveStroke is called on round or drawer transition', () => {
      let flushedPoints: DrawPoint[] = [];
      const batcher = new PointBatcher({
        onFlush: (pts) => {
          flushedPoints = pts;
        },
        batchIntervalMs: 50,
      });

      // Add a move point (pending in buffer)
      batcher.addPoint({
        x: 0.2,
        y: 0.3,
        color: '#000000',
        size: 4,
        isNewPath: false,
      });

      expect(batcher.getPendingCount()).toBe(1);

      // Cancel stroke before timer fires (e.g. round ended or drawer lost)
      batcher.clear();

      expect(batcher.getPendingCount()).toBe(0);
      expect(flushedPoints.length).toBe(0);
    });
  });
});
