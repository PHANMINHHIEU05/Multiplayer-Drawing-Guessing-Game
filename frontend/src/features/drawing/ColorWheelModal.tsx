import React, { useEffect, useState } from 'react';

interface ColorWheelModalProps {
  isOpen: boolean;
  currentColor: string;
  onClose: () => void;
  onSelectColor: (color: string) => void;
}

const byteToHex = (value: number) =>
  Math.max(0, Math.min(255, Math.round(value))).toString(16).padStart(2, '0');

/** Convert a point in a CSS conic colour wheel to the exact displayed HSV colour. */
export function colorWheelPointToHex(x: number, y: number, radius: number): string {
  const saturation = Math.min(Math.hypot(x, y) / Math.max(radius, 1), 1);
  // CSS conic-gradient starts at 12 o'clock and advances clockwise.
  const hue = (Math.atan2(y, x) * (180 / Math.PI) + 90 + 360) % 360;
  const sector = hue / 60;
  const chroma = saturation;
  const secondary = chroma * (1 - Math.abs((sector % 2) - 1));
  let r = 0;
  let g = 0;
  let b = 0;

  if (sector < 1) [r, g] = [chroma, secondary];
  else if (sector < 2) [r, g] = [secondary, chroma];
  else if (sector < 3) [g, b] = [chroma, secondary];
  else if (sector < 4) [g, b] = [secondary, chroma];
  else if (sector < 5) [r, b] = [secondary, chroma];
  else [r, b] = [chroma, secondary];

  const match = 1 - chroma;
  return `#${byteToHex((r + match) * 255)}${byteToHex((g + match) * 255)}${byteToHex((b + match) * 255)}`;
}

export const ColorWheelModal: React.FC<ColorWheelModalProps> = ({
  isOpen,
  currentColor,
  onClose,
  onSelectColor,
}) => {
  const [hexInput, setHexInput] = useState(currentColor);

  useEffect(() => {
    if (isOpen) setHexInput(currentColor.toLowerCase());
  }, [currentColor, isOpen]);

  if (!isOpen) return null;

  const handleWheelClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left - rect.width / 2;
    const y = e.clientY - rect.top - rect.height / 2;
    const hex = colorWheelPointToHex(x, y, rect.width / 2);
    setHexInput(hex);
    onSelectColor(hex);
  };

  const handleConfirm = () => {
    if (/^#[0-9A-F]{6}$/i.test(hexInput)) {
      onSelectColor(hexInput);
    }
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 select-none animate-fadeIn"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="glass-panel-dark p-6 max-w-xs w-full text-center space-y-4 rounded-3xl shadow-2xl">
        <div className="flex justify-between items-center">
          <h3 className="text-sm font-black text-amber-700 uppercase tracking-wider">
            Bảng Màu Quang Phổ 360°
          </h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-800 transition-colors">
            <span className="material-symbols-outlined text-sm">close</span>
          </button>
        </div>

        {/* 360 Spectrum Wheel */}
        <div
          onClick={handleWheelClick}
          className="w-44 h-44 rounded-full mx-auto border-4 border-[#15375f] shadow-2xl cursor-crosshair relative transform active:scale-95 transition-transform"
          style={{
            background:
              'radial-gradient(circle at center, #fff 0%, rgba(255,255,255,0) 100%), conic-gradient(from 0deg, red, yellow, lime, aqua, blue, magenta, red)',
          }}
        />

        {/* Selected Color Preview & HEX input */}
        <div className="flex items-center justify-center gap-2">
          <div
            className="w-8 h-8 rounded-xl border-2 border-[#15375f] shadow-inner"
            style={{ backgroundColor: hexInput }}
          />
          <input
            type="text"
            value={hexInput}
            onChange={(e) => setHexInput(e.target.value)}
            className="bg-white border-2 border-sky-200 rounded-xl px-3 py-1.5 text-xs font-mono text-slate-800 text-center w-28 outline-none uppercase"
          />
        </div>

        <button
          onClick={handleConfirm}
          className="dg-primary-button bouncy-btn w-full py-2.5 text-xs"
        >
          XÁC NHẬN MÀU NÀY
        </button>
      </div>
    </div>
  );
};
