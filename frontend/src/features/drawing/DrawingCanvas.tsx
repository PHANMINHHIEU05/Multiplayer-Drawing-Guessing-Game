import React, { useRef, useEffect, useState, useCallback, useImperativeHandle, forwardRef } from 'react';
import { DrawPoint } from '../../types/game';
import { usePointBatcher } from './usePointBatcher';

export interface DrawingCanvasHandle {
  clear: () => void;
}

interface DrawingCanvasProps {
  isDrawer: boolean;
  color?: string;
  size?: number;
  isEraser?: boolean;
  onDrawPoint?: (point: DrawPoint) => void;
  onDrawBatch?: (points: DrawPoint[]) => void;
  onClearCanvas?: () => void;
  externalPoints?: DrawPoint[];
  hideInternalToolbar?: boolean;
}

const CANVAS_BG = '#ffffff';

export const DrawingCanvas = forwardRef<DrawingCanvasHandle, DrawingCanvasProps>(({
  isDrawer,
  color: controlledColor,
  size: controlledSize,
  isEraser = false,
  onDrawPoint,
  onDrawBatch,
  onClearCanvas,
  externalPoints = [],
  hideInternalToolbar = true,
}, ref) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const isDrawing = useRef(false);

  const [internalColor, setInternalColor] = useState('#000000');
  const [internalSize] = useState(4);

  const activeColor = isEraser ? CANVAS_BG : (controlledColor ?? internalColor);
  const activeSize = isEraser ? (controlledSize ?? internalSize) * 2.5 : (controlledSize ?? internalSize);

  // Track the last rendered external point index to avoid re-rendering everything
  const lastRenderedIndexRef = useRef(0);

  // ─── Batching Hook for Network Performance ─────────────────────────
  const handleFlushBatch = useCallback((points: DrawPoint[]) => {
    if (points.length === 0) return;

    if (onDrawBatch) {
      onDrawBatch(points);
    } else if (onDrawPoint) {
      for (const point of points) {
        onDrawPoint(point);
      }
    }
  }, [onDrawBatch, onDrawPoint]);

  const batcher = usePointBatcher({
    onFlush: handleFlushBatch,
    batchIntervalMs: 16, // 60 FPS batching
  });

  // ─── Coordinate Normalization Helpers ──────────────────────────────
  const normalizeCoords = useCallback((pixelX: number, pixelY: number): { x: number; y: number } => {
    const canvas = canvasRef.current;
    if (!canvas || canvas.width === 0 || canvas.height === 0) {
      return { x: 0, y: 0 };
    }
    return {
      x: pixelX / canvas.width,
      y: pixelY / canvas.height,
    };
  }, []);

  const denormalizeCoords = useCallback((normX: number, normY: number): { x: number; y: number } => {
    const canvas = canvasRef.current;
    if (!canvas) return { x: 0, y: 0 };
    return {
      x: normX * canvas.width,
      y: normY * canvas.height,
    };
  }, []);

  // ─── Canvas Render Functions ───────────────────────────────────────
  const drawPointOnCanvas = useCallback((point: DrawPoint, ctx?: CanvasRenderingContext2D) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const context = ctx || canvas.getContext('2d');
    if (!context) return;

    const { x, y } = denormalizeCoords(point.x, point.y);

    context.strokeStyle = point.color || '#000000';
    context.lineWidth = point.size || 4;
    context.lineCap = 'round';
    context.lineJoin = 'round';

    if (point.isNewPath) {
      context.beginPath();
      context.moveTo(x, y);
    } else {
      context.lineTo(x, y);
      context.stroke();
    }
  }, [denormalizeCoords]);

  const renderAllPoints = useCallback((
    ctx: CanvasRenderingContext2D,
    points: DrawPoint[],
    width: number,
    height: number
  ) => {
    ctx.fillStyle = CANVAS_BG;
    ctx.fillRect(0, 0, width, height);

    for (const point of points) {
      const x = point.x * width;
      const y = point.y * height;

      ctx.strokeStyle = point.color || '#000000';
      ctx.lineWidth = point.size || 4;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';

      if (point.isNewPath) {
        ctx.beginPath();
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
        ctx.stroke();
      }
    }
  }, []);

  // ─── Canvas Setup & ResizeObserver ─────────────────────────────────
  const initCanvas = useCallback(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const width = container.clientWidth;
    const height = container.clientHeight;

    if (canvas.width !== width || canvas.height !== height) {
      const oldWidth = canvas.width;
      const oldHeight = canvas.height;

      canvas.width = width;
      canvas.height = height;

      ctx.fillStyle = CANVAS_BG;
      ctx.fillRect(0, 0, width, height);

      if (oldWidth > 0 && oldHeight > 0 && externalPoints.length > 0) {
        renderAllPoints(ctx, externalPoints, width, height);
      }
    }
  }, [externalPoints, renderAllPoints]);

  useEffect(() => {
    initCanvas();

    const container = containerRef.current;
    if (!container) return;

    const observer = new ResizeObserver(() => {
      initCanvas();
    });
    observer.observe(container);

    return () => observer.disconnect();
  }, [initCanvas]);

  // ─── Remote Point Rendering (from WebSocket) ────────────────────────
  useEffect(() => {
    if (externalPoints.length === 0) {
      lastRenderedIndexRef.current = 0;
      return;
    }

    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const startIndex = lastRenderedIndexRef.current;
    if (startIndex >= externalPoints.length) return;

    const newPoints = externalPoints.slice(startIndex);

    requestAnimationFrame(() => {
      for (const point of newPoints) {
        drawPointOnCanvas(point, ctx);
      }
      lastRenderedIndexRef.current = externalPoints.length;
    });
  }, [externalPoints, drawPointOnCanvas]);

  // ─── Pointer Event Handlers (Drawer only) ──────────────────────────
  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!isDrawer) return;
    isDrawing.current = true;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();

    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const pixelX = (e.clientX - rect.left) * scaleX;
    const pixelY = (e.clientY - rect.top) * scaleY;

    const { x, y } = normalizeCoords(pixelX, pixelY);

    const point: DrawPoint = {
      x,
      y,
      color: activeColor,
      size: activeSize,
      isNewPath: true,
      timestamp: Date.now(),
    };

    drawPointOnCanvas(point);
    batcher.addPoint(point);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!isDrawer || !isDrawing.current) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();

    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const pixelX = (e.clientX - rect.left) * scaleX;
    const pixelY = (e.clientY - rect.top) * scaleY;

    const { x, y } = normalizeCoords(pixelX, pixelY);

    const point: DrawPoint = {
      x,
      y,
      color: activeColor,
      size: activeSize,
      isNewPath: false,
      timestamp: Date.now(),
    };

    drawPointOnCanvas(point);
    batcher.addPoint(point);
  };

  const handlePointerUp = () => {
    if (isDrawing.current) {
      isDrawing.current = false;
      batcher.flush();
    }
  };

  // ─── Clear Canvas ──────────────────────────────────────────────────
  const clearCanvas = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.fillStyle = CANVAS_BG;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    lastRenderedIndexRef.current = 0;
    batcher.clear();

    if (onClearCanvas) onClearCanvas();
  }, [batcher, onClearCanvas]);

  useImperativeHandle(ref, () => ({
    clear: clearCanvas,
  }));

  // Reset canvas when external points are cleared
  useEffect(() => {
    if (externalPoints.length === 0 && lastRenderedIndexRef.current > 0) {
      const canvas = canvasRef.current;
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      ctx.fillStyle = CANVAS_BG;
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      lastRenderedIndexRef.current = 0;
    }
  }, [externalPoints.length]);

  return (
    <div className="relative w-full h-full flex flex-col">
      <div
        ref={containerRef}
        className="relative flex-1 w-full min-h-[300px] rounded-3xl overflow-hidden bg-white border-4 border-white/60 shadow-2xl"
      >
        <canvas
          ref={canvasRef}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerLeave={handlePointerUp}
          className={`w-full h-full touch-none ${isDrawer ? 'cursor-crosshair' : 'cursor-not-allowed'}`}
        />
        {!isDrawer && (
          <div className="absolute top-3 left-3 bg-slate-900/75 backdrop-blur-md px-3 py-1 rounded-full border border-white/20 text-[11px] text-slate-200 font-bold shadow-md">
            👀 Chế độ xem (Người đoán từ)
          </div>
        )}
      </div>

      {!hideInternalToolbar && isDrawer && (
        <div className="flex items-center justify-between gap-2 mt-2 p-2 bg-white/80 rounded-xl">
          <div className="flex gap-1">
            {['#000000', '#ef4444', '#f59e0b', '#10b981', '#3b82f6', '#8b5cf6'].map((c) => (
              <button
                key={c}
                onClick={() => setInternalColor(c)}
                className="w-6 h-6 rounded-full border border-white shadow"
                style={{ backgroundColor: c }}
              />
            ))}
          </div>
          <button
            onClick={clearCanvas}
            className="px-3 py-1 bg-rose-500 text-white rounded-lg text-xs font-bold"
          >
            Clear
          </button>
        </div>
      )}
    </div>
  );
});

DrawingCanvas.displayName = 'DrawingCanvas';

