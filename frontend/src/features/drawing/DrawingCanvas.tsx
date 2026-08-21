import React, { useRef, useEffect, useState } from 'react';
import { DrawPoint } from '../../types/game';

interface DrawingCanvasProps {
  isDrawer: boolean;
  onDrawPoint?: (point: DrawPoint) => void;
  externalPoints?: DrawPoint[];
}

const COLORS = [
  '#f8fafc', // White
  '#0f172a', // Dark
  '#ef4444', // Red
  '#f97316', // Orange
  '#eab308', // Yellow
  '#22c55e', // Green
  '#3b82f6', // Blue
  '#a855f7', // Purple
  '#ec4899', // Pink
];

export const DrawingCanvas: React.FC<DrawingCanvasProps> = ({
  isDrawer,
  onDrawPoint,
  externalPoints = [],
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const isDrawing = useRef(false);
  const [color, setColor] = useState('#f8fafc');
  const [size, setSize] = useState(4);

  // Setup canvas resolution & draw external points
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Resize canvas display vs buffer
    const width = canvas.parentElement?.clientWidth || 800;
    const height = canvas.parentElement?.clientHeight || 500;

    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
      // Fill background
      ctx.fillStyle = '#090d16';
      ctx.fillRect(0, 0, width, height);
    }
  }, []);

  const drawPointOnCanvas = (point: DrawPoint) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.strokeStyle = point.color || '#f8fafc';
    ctx.lineWidth = point.size || 4;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    if (point.isNewPath) {
      ctx.beginPath();
      ctx.moveTo(point.x, point.y);
    } else {
      ctx.lineTo(point.x, point.y);
      ctx.stroke();
    }
  };

  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!isDrawer) return;
    isDrawing.current = true;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const point: DrawPoint = { x, y, color, size, isNewPath: true };
    drawPointOnCanvas(point);
    if (onDrawPoint) onDrawPoint(point);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!isDrawer || !isDrawing.current) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const point: DrawPoint = { x, y, color, size, isNewPath: false };
    drawPointOnCanvas(point);
    if (onDrawPoint) onDrawPoint(point);
  };

  const handlePointerUp = () => {
    isDrawing.current = false;
  };

  const clearCanvas = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.fillStyle = '#090d16';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
  };

  return (
    <div className="flex flex-col h-full bg-slate-900/80 border border-slate-800 rounded-2xl p-4 shadow-xl">
      <div className="relative flex-1 min-h-[380px] w-full rounded-xl overflow-hidden border border-slate-800 bg-[#090d16]">
        <canvas
          ref={canvasRef}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerLeave={handlePointerUp}
          className={`w-full h-full touch-none ${isDrawer ? 'cursor-crosshair' : 'cursor-not-allowed'}`}
        />
        {!isDrawer && (
          <div className="absolute top-3 left-3 bg-slate-900/80 backdrop-blur-md px-3 py-1 rounded-lg border border-slate-700 text-xs text-slate-400 font-medium">
            👀 View-only Mode (Guesser)
          </div>
        )}
      </div>

      {isDrawer && (
        <div className="flex flex-wrap items-center justify-between gap-3 mt-4 pt-3 border-t border-slate-800">
          <div className="flex items-center gap-1.5">
            {COLORS.map((c) => (
              <button
                key={c}
                onClick={() => setColor(c)}
                className={`w-7 h-7 rounded-full transition-all border-2 ${
                  color === c ? 'scale-110 border-white ring-2 ring-indigo-500/50' : 'border-transparent opacity-80 hover:opacity-100'
                }`}
                style={{ backgroundColor: c }}
              />
            ))}
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-400 font-medium">Size:</span>
              <input
                type="range"
                min="2"
                max="20"
                value={size}
                onChange={(e) => setSize(parseInt(e.target.value))}
                className="w-20 accent-indigo-500 bg-slate-800 rounded cursor-pointer"
              />
            </div>

            <button
              onClick={clearCanvas}
              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700 transition-all"
            >
              Clear
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
