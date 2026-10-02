import React, { useState } from 'react';
import { ColorWheelModal } from './ColorWheelModal';
import { audioManager } from '../../audio/AudioManager';

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
  onClearCanvas,
}) => {
  const [showColorWheel, setShowColorWheel] = useState(false);

  const handleToolSelect = (tool: 'pen' | 'eraser' | 'fill' | 'line' | 'circle' | 'rect') => {
    audioManager.playSFX('tool_click');
    onToolChange?.(tool);
  };

  return (
    <>
      <div className="glass-panel-game !bg-[#e8f6fc] w-16 sm:w-[76px] flex flex-col items-center py-2.5 px-1.5 h-full shrink-0 gap-2 overflow-y-auto custom-scrollbar select-none">
        <span className="text-[9px] font-black uppercase tracking-wider text-sky-800">
          Công cụ
        </span>
        {/* Tool Actions in 2-Column Grid */}
        <div className="grid grid-cols-2 gap-1.5 w-full">
          {/* Bút vẽ */}
          <button
            type="button"
            title="Bút vẽ tự do"
            onClick={() => handleToolSelect('pen')}
            className={`dg-tool-button btn-3d w-full aspect-square rounded-lg flex items-center justify-center font-bold transition-all ${
              activeTool === 'pen'
                ? 'is-active text-slate-900'
                : 'bg-white text-[#15375f] hover:bg-sky-50'
            }`}
          >
            <span className="material-symbols-outlined text-base sm:text-lg">edit</span>
          </button>

          {/* Tẩy nét */}
          <button
            type="button"
            title="Tẩy nét vẽ"
            onClick={() => handleToolSelect('eraser')}
            className={`dg-tool-button btn-3d w-full aspect-square rounded-lg flex items-center justify-center font-bold transition-all ${
              activeTool === 'eraser'
                ? 'is-active text-slate-900'
                : 'bg-white text-[#15375f] hover:bg-sky-50'
            }`}
          >
            <span className="material-symbols-outlined text-base sm:text-lg">ink_eraser</span>
          </button>

          {/* Hình chữ nhật */}
          <button
            type="button"
            title="Vẽ hình chữ nhật"
            onClick={() => handleToolSelect('rect')}
            className={`dg-tool-button btn-3d w-full aspect-square rounded-lg flex items-center justify-center font-bold transition-all ${
              activeTool === 'rect'
                ? 'is-active text-slate-900'
                : 'bg-white text-[#15375f] hover:bg-sky-50'
            }`}
          >
            <span className="material-symbols-outlined text-base sm:text-lg">rectangle</span>
          </button>

          {/* Hình tròn */}
          <button
            type="button"
            title="Vẽ hình tròn"
            onClick={() => handleToolSelect('circle')}
            className={`dg-tool-button btn-3d w-full aspect-square rounded-lg flex items-center justify-center font-bold transition-all ${
              activeTool === 'circle'
                ? 'is-active text-slate-900'
                : 'bg-white text-[#15375f] hover:bg-sky-50'
            }`}
          >
            <span className="material-symbols-outlined text-base sm:text-lg">circle</span>
          </button>

          {/* Thùng sơn đổ màu */}
          <button
            type="button"
            title="Thùng sơn đổ màu"
            onClick={() => handleToolSelect('fill')}
            className={`dg-tool-button btn-3d w-full aspect-square rounded-lg flex items-center justify-center font-bold transition-all ${
              activeTool === 'fill'
                ? 'is-active text-slate-900'
                : 'bg-white text-[#15375f] hover:bg-sky-50'
            }`}
          >
            <span className="material-symbols-outlined text-base sm:text-lg">format_color_fill</span>
          </button>

          {/* Xóa trắng bảng */}
          <button
            type="button"
            title="Xóa trắng bảng vẽ"
            onClick={() => {
              audioManager.playSFX('tool_click');
              onClearCanvas();
            }}
            className="dg-tool-button btn-3d w-full aspect-square rounded-lg bg-rose-500 hover:bg-rose-600 text-white flex items-center justify-center transition-all"
          >
            <span className="material-symbols-outlined text-base sm:text-lg">delete</span>
          </button>
        </div>

        <div className="w-full h-0.5 bg-sky-200 my-0.5" />

        {/* 2-Column Palette */}
        <div className="grid grid-cols-2 gap-1.5 w-full">
          {PALETTE_COLORS.map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => {
                audioManager.playSFX('tool_click');
                onColorChange(c);
                if (activeTool === 'eraser' && onToolChange) {
                  onToolChange('pen');
                }
              }}
              className={`w-6 h-6 rounded-lg transition-transform mx-auto ${
                color.toLowerCase() === c.toLowerCase()
                  ? 'ring-2 ring-[#15375f] ring-offset-1 scale-110 shadow-md'
                  : 'hover:scale-110 opacity-90'
              }`}
              style={{
                backgroundColor: c,
                border: '1px solid rgba(21,55,95,.38)',
              }}
            />
          ))}
        </div>

        {/* 360 Spectrum Rainbow Color Wheel Button */}
        <button
          type="button"
          title="Bảng màu quang phổ 360°"
          onClick={() => setShowColorWheel(true)}
          className="w-8 h-8 sm:w-9 sm:h-9 rounded-full border-2 border-[#15375f] shadow-md hover:scale-110 transition-transform my-1"
          style={{
            background: 'conic-gradient(red, yellow, lime, aqua, blue, magenta, red)',
          }}
        />

        {/* Brush Size Slider */}
        <div className="w-full flex flex-col items-center gap-1 mt-auto pt-1 border-t-2 border-sky-200">
          <div className="flex items-center gap-1 text-[9px] font-black text-sky-800 uppercase">
            <span>Size</span>
            <span className="text-amber-600">{size}px</span>
          </div>
          <input
            type="range"
            min="2"
            max="28"
            value={size}
            onChange={(e) => onSizeChange(parseInt(e.target.value))}
            className="w-full accent-amber-400 cursor-pointer h-1.5 bg-sky-200 rounded-lg"
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
