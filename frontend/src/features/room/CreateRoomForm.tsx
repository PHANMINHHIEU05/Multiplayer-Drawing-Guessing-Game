import React, { useState } from 'react';
import { wsClient } from '../../websocket/WebSocketClient';
import { MessageType } from '../../websocket/protocol';
import { usePlayerStore } from '../../store/playerStore';

interface CreateRoomFormProps {
  onSuccess?: () => void;
}

export const CreateRoomForm: React.FC<CreateRoomFormProps> = ({ onSuccess }) => {
  const { playerId, username } = usePlayerStore((s) => s);
  const [maxPlayers, setMaxPlayers] = useState<number>(8);
  const [totalRounds, setTotalRounds] = useState<number>(5);
  const [drawTime, setDrawTime] = useState<number>(60);
  const [selectedPack, setSelectedPack] = useState<string>('vi');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) {
      setError('Vui lòng nhập tên người chơi trước.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await wsClient.send(MessageType.CREATE_ROOM, {
        playerId,
        username,
        roomName: `Phòng của ${username}`,
        maxPlayers,
        totalRounds,
      });
      if (onSuccess) onSuccess();
    } catch (err: any) {
      setError(err.message || 'Không thể tạo phòng');
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleCreate} className="space-y-4">
      {error && (
        <div className="p-3 bg-rose-500/20 border border-rose-500/40 text-rose-800 rounded-2xl text-xs font-bold">
          ⚠️ {error}
        </div>
      )}

      {/* Row 1: Players Slider & Draw Time */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div className="bg-white/80 p-3 rounded-2xl border border-sky-200/80 shadow-sm">
          <div className="flex justify-between items-center mb-1.5">
            <label className="text-xs font-extrabold text-slate-700">Số Người Chơi</label>
            <span className="text-xs font-black text-primary bg-sky-100 px-2 py-0.5 rounded-lg">
              {maxPlayers} Người
            </span>
          </div>
          <input
            type="range"
            min="2"
            max="12"
            value={maxPlayers}
            onChange={(e) => setMaxPlayers(parseInt(e.target.value))}
            className="w-full accent-primary cursor-pointer h-2 bg-slate-200 rounded-lg"
          />
          <div className="flex justify-between text-[10px] font-bold text-slate-400 mt-1">
            <span>2</span>
            <span>6</span>
            <span>12 max</span>
          </div>
        </div>

        <div className="bg-white/80 p-3 rounded-2xl border border-sky-200/80 shadow-sm">
          <label className="text-xs font-extrabold text-slate-700 block mb-1.5">Thời Gian Vẽ / Vòng</label>
          <div className="flex gap-1.5">
            {[30, 60, 90].map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setDrawTime(t)}
                className={`flex-1 py-1.5 rounded-xl font-extrabold text-xs transition-all ${
                  drawTime === t
                    ? 'bg-primary text-white shadow-sm scale-105'
                    : 'bg-slate-100 text-slate-600 hover:bg-sky-50'
                }`}
              >
                {t}s
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Word Packs Chips */}
      <div className="bg-white/80 p-3 rounded-2xl border border-sky-200/80 shadow-sm">
        <label className="text-xs font-extrabold text-slate-700 block mb-1.5">Gói Từ Khóa Chủ Đề</label>
        <div className="grid grid-cols-3 gap-2">
          {[
            { id: 'vi', label: 'Tiếng Việt', icon: '🇻🇳' },
            { id: 'anime', label: 'Anime & Game', icon: '🎮' },
            { id: 'food', label: 'Đồ Ăn & Vật', icon: '🍕' },
          ].map((pack) => (
            <button
              key={pack.id}
              type="button"
              onClick={() => setSelectedPack(pack.id)}
              className={`p-2 rounded-xl text-xs font-extrabold flex items-center justify-center gap-1 transition-all ${
                selectedPack === pack.id
                  ? 'bg-sky-100 border-2 border-primary text-primary shadow-sm'
                  : 'bg-slate-100/80 border border-slate-200 text-slate-600 hover:bg-slate-100'
              }`}
            >
              <span>{pack.icon}</span>
              <span className="truncate">{pack.label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Rounds Slider */}
      <div className="bg-white/80 p-3 rounded-2xl border border-sky-200/80 shadow-sm flex items-center justify-between gap-3">
        <div className="min-w-24">
          <label className="text-xs font-extrabold text-slate-700 block">Số Vòng Đấu</label>
          <span className="text-xs font-black text-amber-600">{totalRounds} Vòng</span>
        </div>
        <input
          type="range"
          min="3"
          max="10"
          value={totalRounds}
          onChange={(e) => setTotalRounds(parseInt(e.target.value))}
          className="w-full accent-amber-500 cursor-pointer h-2 bg-slate-200 rounded-lg"
        />
      </div>

      {/* 3D Bouncy Create Button */}
      <button
        type="submit"
        disabled={loading}
        className="bouncy-btn w-full py-3.5 bg-emerald-500 hover:bg-emerald-600 text-white font-black text-base rounded-2xl shadow-[0_5px_0_0_#059669] flex items-center justify-center gap-2 transition-all disabled:opacity-60 mt-2"
      >
        <span className="material-symbols-outlined text-xl">add_circle</span>
        <span>{loading ? 'Đang tạo phòng...' : 'BẮT ĐẦU TẠO PHÒNG'}</span>
      </button>
    </form>
  );
};

