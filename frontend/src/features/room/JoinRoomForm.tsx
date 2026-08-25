import React, { useState, useRef } from 'react';
import { wsClient } from '../../websocket/WebSocketClient';
import { MessageType } from '../../websocket/protocol';
import { usePlayerStore } from '../../store/playerStore';

interface JoinRoomFormProps {
  onSuccess?: () => void;
}

export const JoinRoomForm: React.FC<JoinRoomFormProps> = ({ onSuccess }) => {
  const { playerId, username } = usePlayerStore((s) => s);
  const [digits, setDigits] = useState<string[]>(['', '', '', '', '', '']);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  const fullRoomId = digits.join('').toUpperCase();

  const handleDigitChange = (index: number, val: string) => {
    const char = val.slice(-1).toUpperCase();
    const newDigits = [...digits];
    newDigits[index] = char;
    setDigits(newDigits);

    if (char && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').trim().toUpperCase().slice(0, 6);
    const newDigits = [...digits];
    for (let i = 0; i < pasted.length; i++) {
      newDigits[i] = pasted[i];
    }
    setDigits(newDigits);
    const nextIndex = Math.min(pasted.length, 5);
    inputRefs.current[nextIndex]?.focus();
  };

  const executeJoin = async (targetRoomId: string) => {
    if (!username.trim()) {
      setError('Vui lòng nhập tên người chơi trước.');
      return;
    }
    if (!targetRoomId.trim()) {
      setError('Vui lòng nhập mã phòng hợp lệ.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await wsClient.send(MessageType.JOIN_ROOM, {
        roomId: targetRoomId.trim(),
        playerId,
        username,
      });
      if (onSuccess) onSuccess();
    } catch (err: any) {
      setError(err.message || 'Không thể vào phòng');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    executeJoin(fullRoomId);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && (
        <div className="p-3 bg-rose-500/20 border border-rose-500/40 text-rose-800 rounded-2xl text-xs font-bold">
          ⚠️ {error}
        </div>
      )}

      {/* 6 Digit Box Inputs */}
      <div className="bg-white/80 p-4 rounded-2xl border border-sky-200/80 shadow-sm text-center">
        <label className="text-xs font-extrabold text-slate-700 block mb-3">
          Nhập Mã Phòng 6 Ký Tự
        </label>
        <div className="flex justify-center gap-1.5 sm:gap-2.5" onPaste={handlePaste}>
          {digits.map((digit, i) => (
            <input
              key={i}
              ref={(el) => (inputRefs.current[i] = el)}
              type="text"
              maxLength={1}
              value={digit}
              onChange={(e) => handleDigitChange(i, e.target.value)}
              onKeyDown={(e) => handleKeyDown(i, e)}
              className="w-10 h-12 sm:w-11 sm:h-14 text-center font-black text-xl sm:text-2xl bg-white border-2 border-sky-300 focus:border-primary focus:ring-2 focus:ring-sky-200 rounded-xl uppercase outline-none shadow-inner transition-all text-primary"
            />
          ))}
        </div>

        <button
          type="submit"
          disabled={loading || fullRoomId.length < 3}
          className="bouncy-btn w-full max-w-xs mx-auto py-3 bg-primary hover:bg-primary-dark text-white font-black text-sm rounded-2xl shadow-[0_4px_0_0_#1565C0] mt-4 flex items-center justify-center gap-2 transition-all disabled:opacity-50"
        >
          <span>{loading ? 'Đang vào...' : 'VÀO PHÒNG NGAY'}</span>
          <span className="material-symbols-outlined text-base">arrow_forward</span>
        </button>
      </div>

      {/* Public Rooms List */}
      <div className="bg-white/80 p-3 rounded-2xl border border-sky-200/80 shadow-sm">
        <div className="flex justify-between items-center mb-2 px-1">
          <label className="text-xs font-extrabold text-slate-500 uppercase">Phòng Chờ Phổ Biến</label>
          <span className="text-[10px] font-bold text-sky-600">Đang hoạt động</span>
        </div>
        <div className="space-y-2 max-h-32 overflow-y-auto pr-1 custom-scrollbar">
          {[
            { id: 'VM5CZD', name: 'Phòng Vui Vẻ #VM5CZD', players: '4/8 ng', topic: 'Tiếng Việt' },
            { id: 'DANK99', name: 'Hội Họa Sĩ Pro #DANK99', players: '6/8 ng', topic: 'Anime' },
          ].map((room) => (
            <div
              key={room.id}
              onClick={() => {
                const chars = room.id.split('');
                const newDigits = ['', '', '', '', '', ''];
                chars.forEach((c, idx) => (newDigits[idx] = c));
                setDigits(newDigits);
              }}
              className="flex items-center justify-between p-2 rounded-xl bg-slate-50 hover:bg-sky-50 border border-slate-200 hover:border-primary transition-all cursor-pointer group"
            >
              <div className="flex items-center gap-2">
                <span className="text-base">🎨</span>
                <div>
                  <div className="font-bold text-xs text-slate-800 group-hover:text-primary transition-colors">
                    {room.name}
                  </div>
                  <div className="text-[10px] text-slate-400 font-semibold">{room.topic}</div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-extrabold text-primary bg-sky-100 px-2 py-0.5 rounded-lg">
                  {room.players}
                </span>
                <span className="material-symbols-outlined text-primary text-sm opacity-0 group-hover:opacity-100 transition-opacity">
                  chevron_right
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </form>
  );
};

