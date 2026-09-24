import React, { useState } from "react";
import { Player } from "../types/room";

interface PlayerListProps {
  players: Player[];
  hostPlayerId: string;
  currentPlayerId: string;
  drawerId?: string;
  onKick?: (targetPlayerId: string) => void;
  canKick?: boolean;
}

const AVATAR_BG_COLORS = [
  "bg-amber-200 text-amber-800",
  "bg-sky-200 text-sky-800",
  "bg-emerald-200 text-emerald-800",
  "bg-purple-200 text-purple-800",
  "bg-pink-200 text-pink-800",
  "bg-indigo-200 text-indigo-800",
];

export const PlayerList: React.FC<PlayerListProps> = ({
  players,
  hostPlayerId,
  currentPlayerId,
  drawerId,
  onKick,
  canKick = false,
}) => {
  const [kickConfirmTarget, setKickConfirmTarget] = useState<string | null>(
    null,
  );

  return (
    <div className="glass-panel-game p-4 shadow-lg select-none">
      <div className="flex justify-between items-center mb-3">
        <h3 className="text-xs font-black text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
          <span>👥</span> Người chơi trong phòng ({players.length})
        </h3>
      </div>
      <div className="space-y-2.5 max-h-72 overflow-y-auto pr-1 custom-scrollbar">
        {players.map((player, idx) => {
          const isCurrent = player.playerId === currentPlayerId;
          const isHost = player.playerId === hostPlayerId;
          const isDrawer = player.playerId === drawerId;
          const isConnected = player.connected !== false;
          const avatarColor = AVATAR_BG_COLORS[idx % AVATAR_BG_COLORS.length];

          return (
            <div
              key={player.playerId}
              className={`flex items-center justify-between px-3.5 py-3 rounded-2xl border transition-all ${
                isCurrent
                  ? "bg-sky-100/90 border-primary text-slate-800 shadow-sm ring-1 ring-primary/30"
                  : "bg-white/85 border-slate-200 text-slate-700 hover:bg-white"
              }`}
            >
              {/* Left side: Avatar + Username + Local Player tag + Presence */}
              <div className="flex items-center gap-3 min-w-0">
                <div
                  className={`w-10 h-10 rounded-full flex items-center justify-center text-xs font-black border-2 border-white shadow-sm shrink-0 ${avatarColor}`}
                >
                  {player.username.charAt(0).toUpperCase()}
                </div>
                <div className="min-w-0">
                  <div className="text-xs font-black flex items-center gap-1.5 text-slate-800 truncate">
                    <span className="truncate">{player.username}</span>
                    {isCurrent && (
                      <span className="text-[9px] bg-primary text-white font-black px-1.5 py-0.5 rounded-md shrink-0">
                        Bạn
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-1 mt-0.5">
                    {isConnected ? (
                      <span className="flex items-center gap-1 text-[10px] font-bold text-emerald-600">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                        Đã kết nối
                      </span>
                    ) : (
                      <span className="flex items-center gap-1 text-[10px] font-bold text-amber-600 animate-pulse">
                        <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-ping" />
                        Đang kết nối lại...
                      </span>
                    )}
                  </div>
                </div>
              </div>

              {/* Right side: Role / Readiness Badges & Contextual Kick */}
              <div className="flex items-center gap-2 shrink-0">
                {isDrawer && (
                  <span className="text-[10px] bg-amber-100 text-amber-800 px-2.5 py-1 rounded-xl border border-amber-300 font-black flex items-center gap-1 shadow-sm">
                    ✏️ Người vẽ
                  </span>
                )}

                {isHost ? (
                  <span className="text-[10px] bg-purple-100 text-purple-800 px-2.5 py-1 rounded-xl border border-purple-300 font-black flex items-center gap-1 shadow-sm">
                    👑 Chủ phòng
                  </span>
                ) : (
                  <span
                    className={`text-[10px] px-2.5 py-1 rounded-xl border font-black flex items-center gap-1 shadow-sm ${
                      player.ready
                        ? "bg-emerald-100 text-emerald-800 border-emerald-300"
                        : "bg-slate-100 text-slate-500 border-slate-200"
                    }`}
                  >
                    {player.ready ? "✓ Sẵn sàng" : "Chưa sẵn sàng"}
                  </span>
                )}

                {/* Contextual Kick Action (Host-only, non-host players, WAITING state) */}
                {canKick && !isHost && (
                  <div className="ml-1">
                    {kickConfirmTarget === player.playerId ? (
                      <div className="flex items-center gap-1.5 bg-rose-50 border border-rose-200 px-2 py-1 rounded-xl shadow-sm">
                        <span className="text-[10px] font-bold text-rose-800 whitespace-nowrap">
                          Mời {player.username} ra?
                        </span>
                        <button
                          type="button"
                          onClick={() => {
                            if (onKick) onKick(player.playerId);
                            setKickConfirmTarget(null);
                          }}
                          className="text-[10px] font-black bg-rose-500 hover:bg-rose-600 text-white px-2 py-0.5 rounded-lg shadow-sm transition-all"
                        >
                          Mời ra
                        </button>
                        <button
                          type="button"
                          onClick={() => setKickConfirmTarget(null)}
                          className="text-[10px] font-bold text-slate-500 hover:text-slate-700 px-1 transition-all"
                        >
                          Hủy
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setKickConfirmTarget(player.playerId)}
                        className="text-[10px] font-bold text-slate-400 hover:text-rose-600 hover:bg-rose-50 px-2 py-1 rounded-xl border border-transparent hover:border-rose-200 transition-all flex items-center gap-1"
                        title={`Mời ${player.username} khỏi phòng`}
                      >
                        <span>Mời ra</span>
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
