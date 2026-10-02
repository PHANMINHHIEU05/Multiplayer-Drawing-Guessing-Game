import React from "react";
import { PlayerScore } from "../types/game";

interface ScoreboardProps {
  scores: PlayerScore[];
  currentPlayerId: string;
  currentDrawerId?: string;
}

const AVATAR_BG_COLORS = [
  "bg-amber-200 text-amber-800",
  "bg-sky-200 text-sky-800",
  "bg-emerald-200 text-emerald-800",
  "bg-purple-200 text-purple-800",
  "bg-pink-200 text-pink-800",
  "bg-indigo-200 text-indigo-800",
];

export const Scoreboard: React.FC<ScoreboardProps> = ({
  scores,
  currentPlayerId,
  currentDrawerId,
}) => {
  const sortedScores = [...scores].sort((a, b) => b.score - a.score);

  return (
    <div className="glass-panel-game w-full h-full flex flex-col overflow-hidden select-none">
      <div className="dg-panel-heading !justify-center shrink-0">
        <span>🏆</span> Bảng Xếp Hạng
      </div>

      <div className="flex-1 overflow-y-auto p-2 space-y-2 custom-scrollbar">
        {sortedScores.map((s, index) => {
          const isCurrent = s.playerId === currentPlayerId;
          const isDrawer = s.playerId === currentDrawerId;
          const avatarColor = AVATAR_BG_COLORS[index % AVATAR_BG_COLORS.length];
          const initial = (s.username || "P").charAt(0).toUpperCase();

          return (
            <div
              key={s.playerId}
              className={`flex items-center gap-2.5 p-2 rounded-xl border-2 transition-all ${
                isDrawer
                  ? "bg-amber-100 border-amber-400 shadow-md"
                  : isCurrent
                    ? "bg-sky-100 border-sky-500 shadow-sm"
                    : "bg-white border-slate-200 hover:border-sky-300"
              }`}
            >
              {/* Avatar with status icon */}
              <div className="relative shrink-0">
                <div
                  className={`w-8 h-8 sm:w-9 sm:h-9 rounded-full flex items-center justify-center font-black text-xs sm:text-sm border-2 border-[#15375f] shadow-sm ${avatarColor}`}
                >
                  {initial}
                </div>

                {isDrawer && (
                  <div className="absolute -bottom-1 -right-1 bg-amber-400 text-slate-950 rounded-full p-0.5 shadow text-[9px] font-bold">
                    ✏️
                  </div>
                )}

                {s.hasGuessed && !isDrawer && (
                  <div className="absolute -bottom-1 -right-1 bg-emerald-500 text-white rounded-full p-0.5 shadow text-[9px] font-bold">
                    ✓
                  </div>
                )}
              </div>

              {/* Player Name & Score */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-1">
                  <span className="font-extrabold text-xs text-slate-800 truncate">
                    {s.username}
                  </span>
                  {isCurrent && (
                    <span className="text-[9px] font-black text-white bg-sky-500 px-1 rounded">
                      Bạn
                    </span>
                  )}
                </div>
                <div className="text-[11px] font-black text-amber-600">
                  {s.score}{" "}
                  <span className="text-[9px] font-semibold text-slate-500">
                    điểm
                  </span>
                </div>
              </div>

              {/* Rank Badge */}
              <div className="shrink-0 text-right">
                {index === 0 && <span className="text-sm">🥇</span>}
                {index === 1 && <span className="text-sm">🥈</span>}
                {index === 2 && <span className="text-sm">🥉</span>}
                {index > 2 && (
                  <span className="text-[10px] font-black text-slate-400 px-1">
                    #{index + 1}
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
