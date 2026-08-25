import React, { useState } from 'react';

interface ColorWheelModalProps {
  isOpen: boolean;
  currentColor: string;
  onClose: () => void;
  onSelectColor: (color: string) => void;
}

export const ColorWheelModal: React.FC<ColorWheelModalProps> = ({
  isOpen,
  currentColor,
  onClose,
  onSelectColor,
}) => {
  const [hexInput, setHexInput] = useState(currentColor);

  if (!isOpen) return null;

  const handleWheelClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left - rect.width / 2;
    const y = e.clientY - rect.top - rect.height / 2;
    const angle = Math.atan2(y, x) * (180 / Math.PI) + 180; // 0 - 360
    const distance = Math.min(Math.sqrt(x * x + y * y) / (rect.width / 2), 1.0); // 0.0 - 1.0

    // Convert HSV to Hex (S = distance, V = 1.0)
    const h = angle / 60;
    const c = distance;
    const xVal = c * (1 - Math.abs((h % 2) - 1));
    let r = 0, g = 0, b = 0;

    if (h >= 0 && h < 1) { r = c; g = xVal; }
    else if (h >= 1 && h < 2) { r = xVal; g = c; }
    else if (h >= 2 && h < 3) { g = c; b = xVal; }
    else if (h >= 3 && h < 4) { g = xVal; b = c; }
    else if (h >= 4 && h < 5) { r = xVal; b = c; }
    else if (h >= 5 && h <= 6) { r = c; b = xVal; }

    const m = 1 - c;
    const rByte = Math.round((r + m) * 255);
    const gByte = Math.round((g + m) * 255);
    const bByte = Math.round((b + m) * 255);

    const hex = `#${((1 << 24) + (rByte << 16) + (gByte << 8) + bByte).toString(16).slice(1)}`;
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
      <div className="glass-panel-dark p-6 max-w-xs w-full text-center space-y-4 rounded-3xl border-2 border-white/40 shadow-2xl">
        <div className="flex justify-between items-center">
          <h3 className="text-sm font-black text-amber-300 uppercase tracking-wider">
            Bảng Màu Quang Phổ 360°
          </h3>
          <button onClick={onClose} className="text-white/60 hover:text-white transition-colors">
            <span className="material-symbols-outlined text-sm">close</span>
          </button>
        </div>

        {/* 360 Spectrum Wheel */}
        <div
          onClick={handleWheelClick}
          className="w-44 h-44 rounded-full mx-auto border-4 border-white shadow-2xl cursor-crosshair relative transform active:scale-95 transition-transform"
          style={{
            background: 'conic-gradient(red, yellow, lime, aqua, blue, magenta, red)',
          }}
        >
          <div className="absolute inset-0 rounded-full bg-[radial-gradient(circle,white_0%,transparent_70%)] opacity-60 pointer-events-none" />
        </div>

        {/* Selected Color Preview & HEX input */}
        <div className="flex items-center justify-center gap-2">
          <div
            className="w-8 h-8 rounded-xl border-2 border-white shadow-inner"
            style={{ backgroundColor: hexInput }}
          />
          <input
            type="text"
            value={hexInput}
            onChange={(e) => setHexInput(e.target.value)}
            className="bg-white/20 border border-white/40 rounded-xl px-3 py-1.5 text-xs font-mono text-white text-center w-28 outline-none uppercase"
          />
        </div>

        <button
          onClick={handleConfirm}
          className="bouncy-btn w-full py-2.5 bg-primary hover:bg-primary-dark text-white font-extrabold text-xs rounded-xl shadow-[0_3px_0_0_#1565C0] transition-all"
        >
          XÁC NHẬN MÀU NÀY
        </button>
      </div>
    </div>
  );
};
