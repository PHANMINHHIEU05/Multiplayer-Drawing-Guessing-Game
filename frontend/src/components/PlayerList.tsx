import React, { useState } from "react";
import { Mic, MicOff, VolumeX } from "lucide-react";
import { Player } from "../types/room";
import { useVoiceStore } from "../store/voiceStore";

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

  const isVoiceRoomEnabled = useVoiceStore((s) => s.isVoiceRoomEnabled);
  const speakingPlayers = useVoiceStore((s) => s.speakingPlayers);
  const peerVoiceStates = useVoiceStore((s) => s.peerVoiceStates);
  const isLocalMicOn = useVoiceStore((s) => s.isMicOn);
  const isLocalMuted = useVoiceStore((s) => s.isMuted);


  return (
    <div className="glass-panel-game overflow-hidden select-none">
      <div className="dg-panel-heading">
        <h3 className="flex items-center gap-1.5">
          <span>👥</span> Người chơi trong phòng ({players.length})
        </h3>
      </div>
      <div className="space-y-2.5 max-h-72 overflow-y-auto p-3 custom-scrollbar">
        {players.map((player, idx) => {
          const isCurrent = player.playerId === currentPlayerId;
          const isHost = player.playerId === hostPlayerId;
          const isDrawer = player.playerId === drawerId;
          const isConnected = player.connected !== false;
          const avatarColor = AVATAR_BG_COLORS[idx % AVATAR_BG_COLORS.length];
          const isSpeaking = Boolean(speakingPlayers[player.playerId]);
          const peerState = peerVoiceStates[player.playerId];

          return (
            <div
              key={player.playerId}
              className={`flex items-center justify-between px-3.5 py-3 rounded-xl border-2 transition-all ${
                isCurrent
                  ? "bg-sky-100 border-sky-500 text-slate-800 shadow-sm"
                  : "bg-white border-slate-200 text-slate-700 hover:border-sky-300"
              }`}
            >
              {/* Left side: Avatar + Username + Local Player tag + Presence */}
              <div className="flex items-center gap-3 min-w-0">
                <div
                  className={`w-10 h-10 rounded-full flex items-center justify-center text-xs font-black border-2 border-white shadow-sm shrink-0 transition-all ${avatarColor} ${
                    isSpeaking
                      ? "ring-4 ring-emerald-400 ring-offset-2 ring-offset-white animate-pulse"
                      : ""
                  }`}
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
                    {isSpeaking && (
                      <span className="text-[9px] font-black bg-emerald-500 text-white px-1.5 py-0.2 rounded-md animate-pulse">
                        Đang nói
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

              {/* Right side: Voice Icon + Role / Readiness Badges & Contextual Kick */}
              <div className="flex items-center gap-2 shrink-0">
                {isVoiceRoomEnabled && (
                  <div className="flex items-center gap-1">
                    {isCurrent ? (
                      !isLocalMicOn ? (
                        <span title="Chưa bật micro" className="p-1 rounded-lg bg-slate-100 text-slate-400">
                          <MicOff className="w-3.5 h-3.5" />
                        </span>
                      ) : isLocalMuted ? (
                        <span title="Micro đã tắt tiếng" className="p-1 rounded-lg bg-rose-100 text-rose-600">
                          <MicOff className="w-3.5 h-3.5" />
                        </span>
                      ) : (
                        <span
                          title={isSpeaking ? "Đang nói..." : "Micro đang bật"}
                          className={`p-1 rounded-lg ${
                            isSpeaking
                              ? "bg-emerald-500 text-white animate-pulse"
                              : "bg-emerald-100 text-emerald-700"
                          }`}
                        >
                          <Mic className="w-3.5 h-3.5" />
                        </span>
                      )
                    ) : peerState?.isDeafened ? (
                      <span title="Đang tắt tiếng nghe" className="p-1 rounded-lg bg-slate-100 text-slate-400">
                        <VolumeX className="w-3.5 h-3.5" />
                      </span>
                    ) : peerState?.isMuted ? (
                      <span title="Micro đã tắt tiếng" className="p-1 rounded-lg bg-rose-100 text-rose-500">
                        <MicOff className="w-3.5 h-3.5" />
                      </span>
                    ) : peerState && !peerState.isMuted ? (
                      <span
                        title={isSpeaking ? "Đang nói..." : "Micro đang bật"}
                        className={`p-1 rounded-lg ${
                          isSpeaking
                            ? "bg-emerald-500 text-white animate-pulse"
                            : "bg-emerald-100 text-emerald-700"
                        }`}
                      >
                        <Mic className="w-3.5 h-3.5" />
                      </span>
                    ) : (
                      <span title="Chưa kết nối voice" className="p-1 rounded-lg bg-slate-100 text-slate-300">
                        <MicOff className="w-3.5 h-3.5" />
                      </span>
                    )}
                  </div>
                )}

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
