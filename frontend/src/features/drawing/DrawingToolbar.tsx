import React, { useState } from 'react';
import { ColorWheelModal } from './ColorWheelModal';

interface DrawingToolbarProps {
  color: string;
  size: number;
  opacity?: number;
  activeTool?: 'pen' | 'eraser' | 'fill' | 'line' | 'circle' | 'rect';
  onColorChange: (color: string) => void;
  onSizeChange: (size: number) => void;
  onOpacityChange?: (opacity: number) => void;
  onToolChange?: (tool: 'pen' | 'eraser' | 'fill' | 'line' | 'circle' | 'rect') => void;
  onUndo?: () => void;
  onClearCanvas: () => void;
}

const PALETTE_COLORS = [
  '#000000', // Black
  '#ffffff', // White
  '#64748b', // Slate
  '#ef4444', // Red
  '#f97316', // Orange
  '#f59e0b', // Amber/Yellow
  '#10b981', // Emerald
  '#06b6d4', // Cyan
  '#3b82f6', // Blue
  '#8b5cf6', // Purple
  '#ec4899', // Pink
  '#78350f', // Brown
];

export const DrawingToolbar: React.FC<DrawingToolbarProps> = ({
  color,
  size,
  activeTool = 'pen',
  onColorChange,
  onSizeChange,
  onToolChange,
  onUndo,
  onClearCanvas,
}) => {
  const [showColorWheel, setShowColorWheel] = useState(false);

  return (
    <>
      <div className="glass-panel-game w-16 sm:w-20 flex flex-col items-center py-3 px-1.5 h-full shrink-0 gap-2 overflow-y-auto custom-scrollbar select-none">
        {/* Tool Actions */}
        <div className="flex flex-col gap-1.5 w-full">
          <button
            type="button"
            title="Bút vẽ"
            onClick={() => onToolChange && onToolChange('pen')}
            className={`btn-3d w-full aspect-square rounded-xl flex items-center justify-center font-bold transition-all ${
              activeTool === 'pen'
                ? 'bg-amber-400 text-slate-900 shadow-[0_3px_0_0_#d97706]'
                : 'bg-white/20 text-white hover:bg-white/30'
            }`}
          >
            <span className="material-symbols-outlined text-lg sm:text-xl">edit</span>
          </button>

          <button
            type="button"
            title="Tẩy nét"
            onClick={() => onToolChange && onToolChange('eraser')}
            className={`btn-3d w-full aspect-square rounded-xl flex items-center justify-center font-bold transition-all ${
              activeTool === 'eraser'
                ? 'bg-amber-400 text-slate-900 shadow-[0_3px_0_0_#d97706]'
                : 'bg-white/20 text-white hover:bg-white/30'
            }`}
          >
            <span className="material-symbols-outlined text-lg sm:text-xl">ink_eraser</span>
          </button>

          {onUndo && (
            <button
              type="button"
              title="Hoàn tác"
              onClick={onUndo}
              className="btn-3d w-full aspect-square rounded-xl bg-white/20 text-white hover:bg-white/30 flex items-center justify-center transition-all"
            >
              <span className="material-symbols-outlined text-lg sm:text-xl">undo</span>
            </button>
          )}

          <button
            type="button"
            title="Xóa trắng bảng"
            onClick={onClearCanvas}
            className="btn-3d w-full aspect-square rounded-xl bg-rose-500/80 hover:bg-rose-500 text-white flex items-center justify-center transition-all shadow-[0_2px_0_0_#9f1239]"
          >
            <span className="material-symbols-outlined text-lg sm:text-xl">delete</span>
          </button>
        </div>

        <div className="w-full h-px bg-white/30 my-0.5" />

        {/* 2-Column Palette */}
        <div className="grid grid-cols-2 gap-1.5 w-full">
          {PALETTE_COLORS.map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => {
                onColorChange(c);
                if (activeTool === 'eraser' && onToolChange) {
                  onToolChange('pen');
                }
              }}
              className={`w-6 h-6 rounded-lg transition-transform mx-auto ${
                color.toLowerCase() === c.toLowerCase()
                  ? 'ring-2 ring-white scale-110 shadow-md'
                  : 'hover:scale-110 opacity-90'
              }`}
              style={{
                backgroundColor: c,
                border: c === '#ffffff' ? '1px solid rgba(0,0,0,0.2)' : '1px solid rgba(255,255,255,0.4)',
              }}
            />
          ))}
        </div>

        {/* 360 Spectrum Rainbow Color Wheel Button */}
        <button
          type="button"
          title="Bảng màu quang phổ 360°"
          onClick={() => setShowColorWheel(true)}
          className="w-8 h-8 sm:w-9 sm:h-9 rounded-full border-2 border-white shadow-md hover:scale-110 transition-transform my-1"
          style={{
            background: 'conic-gradient(red, yellow, lime, aqua, blue, magenta, red)',
          }}
        />

        {/* Brush Size Slider */}
        <div className="w-full flex flex-col items-center gap-1 mt-auto pt-1 border-t border-white/20">
          <div className="flex items-center gap-1 text-[9px] font-black text-sky-100 uppercase">
            <span>Size</span>
            <span className="text-amber-300">{size}px</span>
          </div>
          <input
            type="range"
            min="2"
            max="28"
            value={size}
            onChange={(e) => onSizeChange(parseInt(e.target.value))}
            className="w-full accent-amber-400 cursor-pointer h-1.5 bg-white/30 rounded-lg"
          />
        </div>
      </div>

      {/* Color Wheel Modal */}
      <ColorWheelModal
        isOpen={showColorWheel}
        currentColor={color}
        onClose={() => setShowColorWheel(false)}
        onSelectColor={(newColor) => {
          onColorChange(newColor);
          if (activeTool === 'eraser' && onToolChange) {
            onToolChange('pen');
          }
        }}
      />
    </>
  );
};
