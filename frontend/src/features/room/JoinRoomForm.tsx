import React, { useEffect, useState, useRef } from "react";
import { wsClient } from "../../websocket/WebSocketClient";
import { MessageType } from "../../websocket/protocol";
import { usePlayerStore } from "../../store/playerStore";
import { translateError } from "../../utils/errorTranslation";
import { useConnectionStore } from "../../store/connectionStore";

interface PublicRoom {
  roomId: string;
  name: string;
  status: string;
  playerCount: number;
  maxPlayers: number;
}

interface JoinRoomFormProps {
  onSuccess?: () => void;
}

export const JoinRoomForm: React.FC<JoinRoomFormProps> = ({ onSuccess }) => {
  const { playerId, username } = usePlayerStore((s) => s);
  const connectionStatus = useConnectionStore((s) => s.status);
  const [digits, setDigits] = useState<string[]>(["", "", "", "", "", ""]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [publicRooms, setPublicRooms] = useState<PublicRoom[]>([]);
  const [roomsLoading, setRoomsLoading] = useState(false);
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  const fullRoomId = digits.join("").toUpperCase();

  useEffect(() => {
    if (connectionStatus !== "CONNECTED") {
      setPublicRooms([]);
      return;
    }

    let active = true;
    const loadRooms = async () => {
      setRoomsLoading(true);
      try {
        const response = await wsClient.send(MessageType.LIST_ROOMS, {
          limit: 10,
        });
        if (active) {
          setPublicRooms(Array.isArray(response.rooms) ? response.rooms : []);
        }
      } catch {
        if (active) setPublicRooms([]);
      } finally {
        if (active) setRoomsLoading(false);
      }
    };

    void loadRooms();
    const timer = window.setInterval(loadRooms, 5000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [connectionStatus]);

  const handleDigitChange = (index: number, val: string) => {
    const char = val.slice(-1).toUpperCase();
    const newDigits = [...digits];
    newDigits[index] = char;
    setDigits(newDigits);

    if (char && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (
    index: number,
    e: React.KeyboardEvent<HTMLInputElement>,
  ) => {
    if (e.key === "Backspace" && !digits[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    e.preventDefault();
    const pasted = e.clipboardData
      .getData("text")
      .trim()
      .toUpperCase()
      .slice(0, 6);
    const newDigits = [...digits];
    for (let i = 0; i < pasted.length; i++) {
      newDigits[i] = pasted[i];
    }
    setDigits(newDigits);
    const nextIndex = Math.min(pasted.length, 5);
    inputRefs.current[nextIndex]?.focus();
  };

  const executeJoin = async (targetRoomId: string) => {
    if (loading) return;
    if (!username.trim()) {
      setError("Vui lòng nhập tên người chơi trước.");
      return;
    }
    if (!targetRoomId.trim()) {
      setError("Vui lòng nhập mã phòng hợp lệ.");
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
      setError(translateError(err));
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
        <div
          className="flex justify-center gap-1.5 sm:gap-2.5"
          onPaste={handlePaste}
        >
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
          <span>{loading ? "Đang vào..." : "VÀO PHÒNG NGAY"}</span>
          <span className="material-symbols-outlined text-base">
            arrow_forward
          </span>
        </button>
      </div>

      {/* Public Rooms List */}
      <div className="bg-white/80 p-3 rounded-2xl border border-sky-200/80 shadow-sm">
        <div className="flex justify-between items-center mb-2 px-1">
          <label className="text-xs font-extrabold text-slate-500 uppercase">
            Phòng Chờ Phổ Biến
          </label>
          <span className="text-[10px] font-bold text-sky-600">
            Đang hoạt động
          </span>
        </div>
        <div className="space-y-2 max-h-32 overflow-y-auto pr-1 custom-scrollbar">
          {roomsLoading && publicRooms.length === 0 && (
            <div className="py-3 text-center text-xs font-semibold text-slate-400">
              Đang tải danh sách phòng...
            </div>
          )}
          {!roomsLoading && publicRooms.length === 0 && (
            <div className="py-3 text-center text-xs font-semibold text-slate-400">
              Chưa có phòng chờ nào.
            </div>
          )}
          {publicRooms.map((room) => (
            <div
              key={room.roomId}
              onClick={() => {
                const chars = room.roomId.split("");
                const newDigits = ["", "", "", "", "", ""];
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
                  <div className="text-[10px] text-slate-400 font-semibold">
                    Mã phòng: {room.roomId}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-extrabold text-primary bg-sky-100 px-2 py-0.5 rounded-lg">
                  {room.playerCount}/{room.maxPlayers} ng
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
