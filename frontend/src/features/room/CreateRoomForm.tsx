import React, { useEffect, useState } from "react";
import { wsClient } from "../../websocket/WebSocketClient";
import { MessageType } from "../../websocket/protocol";
import { usePlayerStore } from "../../store/playerStore";
import { translateError } from "../../utils/errorTranslation";

interface CreateRoomFormProps {
  onSuccess?: () => void;
}

export const CreateRoomForm: React.FC<CreateRoomFormProps> = ({
  onSuccess,
}) => {
  const { playerId, username } = usePlayerStore((s) => s);
  const [maxPlayers, setMaxPlayers] = useState<number>(8);
  const [totalRounds, setTotalRounds] = useState<number>(5);
  const [drawTime, setDrawTime] = useState<number>(60);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!error) return;
    const timer = window.setTimeout(() => setError(null), 6000);
    return () => window.clearTimeout(timer);
  }, [error]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading) return;
    if (!username.trim()) {
      setError("Vui lòng nhập tên người chơi trước.");
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
        roundDuration: drawTime,
      });
      if (onSuccess) onSuccess();
    } catch (err: any) {
      setError(translateError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleCreate} className="space-y-3">
      {error && (
        <div className="p-3 bg-rose-50 border-2 border-rose-300 text-rose-700 rounded-xl text-xs font-bold">
          ⚠️ {error}
        </div>
      )}

      {/* Row 1: Players Slider & Draw Time */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div className="dg-field-card p-3">
          <div className="flex justify-between items-center mb-1.5">
            <label className="text-xs font-extrabold text-slate-700">
              Số Người Chơi
            </label>
            <span className="text-xs font-black text-sky-700 bg-sky-100 border border-sky-200 px-2 py-0.5 rounded-lg">
              {maxPlayers} Người
            </span>
          </div>
          <input
            type="range"
            min="2"
            max="10"
            value={maxPlayers}
            onChange={(e) => setMaxPlayers(parseInt(e.target.value))}
            className="w-full accent-sky-500 cursor-pointer h-2 bg-slate-200 rounded-lg"
          />
          <div className="flex justify-between text-[10px] font-bold text-slate-400 mt-1">
            <span>2</span>
            <span>6</span>
            <span>10 max</span>
          </div>
        </div>

        <div className="dg-field-card p-3">
          <label className="text-xs font-extrabold text-slate-700 block mb-1.5">
            Thời Gian Vẽ / Vòng
          </label>
          <div className="flex gap-1.5">
            {[30, 60, 90].map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setDrawTime(t)}
                className={`flex-1 py-1.5 rounded-xl font-extrabold text-xs transition-all ${
                  drawTime === t
                    ? "bg-sky-500 text-white border-2 border-[#15375f] shadow-[0_2px_0_rgba(21,55,95,.3)]"
                    : "bg-slate-100 text-slate-600 border-2 border-transparent hover:bg-sky-50"
                }`}
              >
                {t}s
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Rounds Slider */}
      <div className="dg-field-card p-3 flex items-center justify-between gap-3">
        <div className="min-w-24">
          <label className="text-xs font-extrabold text-slate-700 block">
            Số Vòng Đấu
          </label>
          <span className="text-xs font-black text-amber-600">
            {totalRounds} Vòng
          </span>
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
        className="dg-yellow-button bouncy-btn w-full py-3 flex items-center justify-center gap-2 text-sm sm:text-base mt-2"
      >
        <span className="material-symbols-outlined text-xl">add_circle</span>
        <span>{loading ? "Đang tạo phòng..." : "BẮT ĐẦU TẠO PHÒNG"}</span>
      </button>
    </form>
  );
};
