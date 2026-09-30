import React, { useRef, useEffect, useState, useCallback, useImperativeHandle, forwardRef } from 'react';
import { DrawPoint } from '../../types/game';
import { usePointBatcher } from './usePointBatcher';
import { generateStrokeId } from './binaryCodec';
import { audioManager } from '../../audio/AudioManager';

export interface DrawingCanvasHandle {
  clear: () => void;
  cancelActiveStroke: () => void;
}

interface DrawingCanvasProps {
  isDrawer: boolean;
  color?: string;
  size?: number;
  isEraser?: boolean;
  activeTool?: 'pen' | 'eraser' | 'fill' | 'line' | 'circle' | 'rect';
  onDrawPoint?: (point: DrawPoint) => void;
  onDrawBatch?: (points: DrawPoint[]) => void;
  onClearCanvas?: () => void;
  externalPoints?: DrawPoint[];
  hideInternalToolbar?: boolean;
}

const CANVAS_BG = '#ffffff';

/**
 * Fast BFS Flood Fill using 32-bit pixel buffer comparison
 */
function fastFloodFill(
  ctx: CanvasRenderingContext2D,
  startX: number,
  startY: number,
  fillColorHex: string
) {
  const width = ctx.canvas.width;
  const height = ctx.canvas.height;
  if (width === 0 || height === 0) return;

  const startXInt = Math.floor(startX);
  const startYInt = Math.floor(startY);
  if (startXInt < 0 || startXInt >= width || startYInt < 0 || startYInt >= height) return;

  const imgData = ctx.getImageData(0, 0, width, height);
  const data = new Uint32Array(imgData.data.buffer);

  // Convert hex color to 32-bit Little-Endian ABGR: (A << 24) | (B << 16) | (G << 8) | R
  const cleanHex = fillColorHex.replace('#', '');
  const r = parseInt(cleanHex.substring(0, 2), 16) || 0;
  const g = parseInt(cleanHex.substring(2, 4), 16) || 0;
  const b = parseInt(cleanHex.substring(4, 6), 16) || 0;
  const fillVal = (255 << 24) | (b << 16) | (g << 8) | r;

  const startIdx = startYInt * width + startXInt;
  const targetVal = data[startIdx];

  // If clicked color already matches target color
  if (targetVal === fillVal) return;

  const targetR = targetVal & 0xff;
  const targetG = (targetVal >> 8) & 0xff;
  const targetB = (targetVal >> 16) & 0xff;

  const matches = (color: number) => {
    if (color === fillVal) return false;
    const cr = color & 0xff;
    const cg = (color >> 8) & 0xff;
    const cb = (color >> 16) & 0xff;
    return (
      Math.abs(cr - targetR) <= 32 &&
      Math.abs(cg - targetG) <= 32 &&
      Math.abs(cb - targetB) <= 32
    );
  };

  const queue = new Int32Array(width * height);
  let head = 0;
  let tail = 0;

  queue[tail++] = startIdx;
  data[startIdx] = fillVal;

  while (head < tail) {
    const idx = queue[head++];
    const x = idx % width;
    const y = Math.floor(idx / width);

    // North
    if (y > 0) {
      const up = idx - width;
      if (matches(data[up])) {
        data[up] = fillVal;
        queue[tail++] = up;
      }
    }
    // South
    if (y < height - 1) {
      const down = idx + width;
      if (matches(data[down])) {
        data[down] = fillVal;
        queue[tail++] = down;
      }
    }
    // West
    if (x > 0) {
      const left = idx - 1;
      if (matches(data[left])) {
        data[left] = fillVal;
        queue[tail++] = left;
      }
    }
    // East
    if (x < width - 1) {
      const right = idx + 1;
      if (matches(data[right])) {
        data[right] = fillVal;
        queue[tail++] = right;
      }
    }
  }

  ctx.putImageData(imgData, 0, 0);
}

export const DrawingCanvas = forwardRef<DrawingCanvasHandle, DrawingCanvasProps>(({
  isDrawer,
  color: controlledColor,
  size: controlledSize,
  isEraser = false,
  activeTool = 'pen',
  onDrawPoint,
  onDrawBatch,
  onClearCanvas,
  externalPoints = [],
  hideInternalToolbar = true,
}, ref) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const isDrawing = useRef(false);
  const currentStrokeIdRef = useRef<string>('');

  // Shape drawing state
  const shapeStartRef = useRef<{ pixelX: number; pixelY: number; normX: number; normY: number } | null>(null);
  const currentPosRef = useRef<{ pixelX: number; pixelY: number; normX: number; normY: number } | null>(null);
  const snapshotRef = useRef<ImageData | null>(null);

  const [internalColor, setInternalColor] = useState('#000000');
  const [internalSize] = useState(4);

  const activeColor = controlledColor ?? internalColor;
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

    const isEraserTool = point.tool === 'ERASER';
    if (isEraserTool) {
      context.globalCompositeOperation = 'destination-out';
      context.strokeStyle = 'rgba(0,0,0,1)';
      context.lineWidth = point.size || 16;
    } else {
      context.globalCompositeOperation = 'source-over';
      context.strokeStyle = point.color || '#000000';
      context.lineWidth = point.size || 4;
    }
    context.lineCap = 'round';
    context.lineJoin = 'round';

    if (point.isNewPath) {
      context.beginPath();
      context.moveTo(x, y);
      context.lineTo(x, y);
      context.stroke();
    } else {
      context.lineTo(x, y);
      context.stroke();
    }

    // Always restore default source-over composite operation
    context.globalCompositeOperation = 'source-over';
  }, [denormalizeCoords]);

  const renderAllPoints = useCallback((
    ctx: CanvasRenderingContext2D,
    points: DrawPoint[],
    width: number,
    height: number
  ) => {
    ctx.globalCompositeOperation = 'source-over';
    ctx.fillStyle = CANVAS_BG;
    ctx.fillRect(0, 0, width, height);

    for (const point of points) {
      const x = point.x * width;
      const y = point.y * height;

      if (point.tool === 'ERASER') {
        ctx.globalCompositeOperation = 'destination-out';
        ctx.strokeStyle = 'rgba(0,0,0,1)';
        ctx.lineWidth = point.size || 16;
      } else {
        ctx.globalCompositeOperation = 'source-over';
        ctx.strokeStyle = point.color || '#000000';
        ctx.lineWidth = point.size || 4;
      }
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';

      if (point.isNewPath) {
        ctx.beginPath();
        ctx.moveTo(x, y);
        ctx.lineTo(x, y);
        ctx.stroke();
      } else {
        ctx.lineTo(x, y);
        ctx.stroke();
      }
    }

    // Always restore default source-over composite operation
    ctx.globalCompositeOperation = 'source-over';
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
    if (isDrawer) return;

    if (externalPoints.length === 0) {
      const canvas = canvasRef.current;
      if (canvas) {
        const ctx = canvas.getContext('2d');
        if (ctx) {
          ctx.globalCompositeOperation = 'source-over';
          ctx.clearRect(0, 0, canvas.width, canvas.height);
          ctx.fillStyle = CANVAS_BG;
          ctx.fillRect(0, 0, canvas.width, canvas.height);
        }
      }
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
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const pixelX = (e.clientX - rect.left) * scaleX;
    const pixelY = (e.clientY - rect.top) * scaleY;
    const { x, y } = normalizeCoords(pixelX, pixelY);

    audioManager.playSFX('draw_start');

    // Fill Tool (Flood Fill)
    if (activeTool === 'fill') {
      fastFloodFill(ctx, pixelX, pixelY, activeColor);
      return;
    }

    // Rectangle or Circle Tool
    if (activeTool === 'rect' || activeTool === 'circle') {
      isDrawing.current = true;
      shapeStartRef.current = { pixelX, pixelY, normX: x, normY: y };
      currentPosRef.current = { pixelX, pixelY, normX: x, normY: y };
      snapshotRef.current = ctx.getImageData(0, 0, canvas.width, canvas.height);
      currentStrokeIdRef.current = generateStrokeId();
      return;
    }

    // Pen or Eraser Tool
    isDrawing.current = true;
    const strokeId = generateStrokeId();
    currentStrokeIdRef.current = strokeId;
    const currentTool = isEraser ? 'ERASER' : 'BRUSH';

    const point: DrawPoint = {
      x,
      y,
      color: isEraser ? '#ffffff' : activeColor,
      size: activeSize,
      isNewPath: true,
      tool: currentTool,
      strokeId,
      timestamp: Date.now(),
    };

    drawPointOnCanvas(point);
    batcher.addPoint(point);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!isDrawer || !isDrawing.current) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const pixelX = (e.clientX - rect.left) * scaleX;
    const pixelY = (e.clientY - rect.top) * scaleY;
    const { x, y } = normalizeCoords(pixelX, pixelY);

    currentPosRef.current = { pixelX, pixelY, normX: x, normY: y };

    // Shape Preview
    if (activeTool === 'rect' || activeTool === 'circle') {
      if (!shapeStartRef.current || !snapshotRef.current) return;
      ctx.putImageData(snapshotRef.current, 0, 0);
      ctx.save();
      ctx.globalCompositeOperation = 'source-over';
      ctx.strokeStyle = activeColor;
      ctx.lineWidth = activeSize;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';

      const startX = shapeStartRef.current.pixelX;
      const startY = shapeStartRef.current.pixelY;

      if (activeTool === 'rect') {
        ctx.strokeRect(startX, startY, pixelX - startX, pixelY - startY);
      } else {
        const cx = (startX + pixelX) / 2;
        const cy = (startY + pixelY) / 2;
        const rx = Math.max(1, Math.abs(pixelX - startX) / 2);
        const ry = Math.max(1, Math.abs(pixelY - startY) / 2);
        ctx.beginPath();
        ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
        ctx.stroke();
      }
      ctx.restore();
      return;
    }

    // Pen or Eraser
    const currentTool = isEraser ? 'ERASER' : 'BRUSH';

    const point: DrawPoint = {
      x,
      y,
      color: isEraser ? '#ffffff' : activeColor,
      size: activeSize,
      isNewPath: false,
      tool: currentTool,
      strokeId: currentStrokeIdRef.current,
      timestamp: Date.now(),
    };

    drawPointOnCanvas(point);
    batcher.addPoint(point);
  };

  const handlePointerUp = () => {
    if (!isDrawing.current) return;
    isDrawing.current = false;

    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');

    if ((activeTool === 'rect' || activeTool === 'circle') && shapeStartRef.current && currentPosRef.current && ctx && canvas) {
      if (snapshotRef.current) {
        ctx.putImageData(snapshotRef.current, 0, 0);
      }
      ctx.save();
      ctx.globalCompositeOperation = 'source-over';
      ctx.strokeStyle = activeColor;
      ctx.lineWidth = activeSize;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';

      const strokeId = currentStrokeIdRef.current || generateStrokeId();
      const startNormX = shapeStartRef.current.normX;
      const startNormY = shapeStartRef.current.normY;
      const endNormX = currentPosRef.current.normX;
      const endNormY = currentPosRef.current.normY;

      const startPX = shapeStartRef.current.pixelX;
      const startPY = shapeStartRef.current.pixelY;
      const endPX = currentPosRef.current.pixelX;
      const endPY = currentPosRef.current.pixelY;

      if (activeTool === 'rect') {
        ctx.strokeRect(startPX, startPY, endPX - startPX, endPY - startPY);

        // Synchronize rectangle boundary to other players
        const rectPoints: DrawPoint[] = [
          { x: startNormX, y: startNormY, isNewPath: true, color: activeColor, size: activeSize, tool: 'BRUSH', strokeId, timestamp: Date.now() },
          { x: endNormX, y: startNormY, isNewPath: false, color: activeColor, size: activeSize, tool: 'BRUSH', strokeId, timestamp: Date.now() },
          { x: endNormX, y: endNormY, isNewPath: false, color: activeColor, size: activeSize, tool: 'BRUSH', strokeId, timestamp: Date.now() },
          { x: startNormX, y: endNormY, isNewPath: false, color: activeColor, size: activeSize, tool: 'BRUSH', strokeId, timestamp: Date.now() },
          { x: startNormX, y: startNormY, isNewPath: false, color: activeColor, size: activeSize, tool: 'BRUSH', strokeId, timestamp: Date.now() },
        ];
        handleFlushBatch(rectPoints);
      } else {
        const cx = (startPX + endPX) / 2;
        const cy = (startPY + endPY) / 2;
        const rx = Math.max(1, Math.abs(endPX - startPX) / 2);
        const ry = Math.max(1, Math.abs(endPY - startPY) / 2);
        ctx.beginPath();
        ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
        ctx.stroke();

        // Synchronize circle boundary to other players (36 segments for smooth circle)
        const normCX = (startNormX + endNormX) / 2;
        const normCY = (startNormY + endNormY) / 2;
        const normRX = Math.abs(endNormX - startNormX) / 2;
        const normRY = Math.abs(endNormY - startNormY) / 2;
        const circlePoints: DrawPoint[] = [];
        const STEPS = 36;
        for (let i = 0; i <= STEPS; i++) {
          const angle = (i / STEPS) * 2 * Math.PI;
          const px = normCX + normRX * Math.cos(angle);
          const py = normCY + normRY * Math.sin(angle);
          circlePoints.push({
            x: px,
            y: py,
            isNewPath: i === 0,
            color: activeColor,
            size: activeSize,
            tool: 'BRUSH',
            strokeId,
            timestamp: Date.now(),
          });
        }
        handleFlushBatch(circlePoints);
      }

      ctx.restore();
      shapeStartRef.current = null;
      currentPosRef.current = null;
      snapshotRef.current = null;
      return;
    }

    batcher.flush();
  };

  // ─── Clear & Cancellation Handlers ─────────────────────────────────
  const cancelActiveStroke = useCallback(() => {
    isDrawing.current = false;
    shapeStartRef.current = null;
    currentPosRef.current = null;
    snapshotRef.current = null;
    batcher.cancelActiveStroke();
  }, [batcher]);

  const clearCanvas = useCallback(() => {
    cancelActiveStroke();
    lastRenderedIndexRef.current = 0;

    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.globalCompositeOperation = 'source-over';
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = CANVAS_BG;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
  }, [cancelActiveStroke]);

  useImperativeHandle(ref, () => ({
    clear: clearCanvas,
    cancelActiveStroke,
  }));

  // Cancel active stroke if player loses drawer role
  useEffect(() => {
    if (!isDrawer && isDrawing.current) {
      cancelActiveStroke();
    }
  }, [isDrawer, cancelActiveStroke]);

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
          className={`w-full h-full touch-none ${
            !isDrawer
              ? 'cursor-not-allowed'
              : isEraser
              ? 'cursor-eraser'
              : activeTool === 'fill'
              ? 'cursor-bucket'
              : activeTool === 'rect' || activeTool === 'circle'
              ? 'cursor-crosshair'
              : 'cursor-pencil'
          }`}
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
            onClick={() => {
              clearCanvas();
              if (onClearCanvas) onClearCanvas();
            }}
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

