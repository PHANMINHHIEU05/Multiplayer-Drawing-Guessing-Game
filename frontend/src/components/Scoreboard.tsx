import React from 'react';
import { PlayerScore } from '../types/game';

interface ScoreboardProps {
  scores: PlayerScore[];
  currentPlayerId: string;
  currentDrawerId?: string;
}

const AVATAR_BG_COLORS = [
  'bg-amber-200 text-amber-800',
  'bg-sky-200 text-sky-800',
  'bg-emerald-200 text-emerald-800',
  'bg-purple-200 text-purple-800',
  'bg-pink-200 text-pink-800',
  'bg-indigo-200 text-indigo-800',
];

export const Scoreboard: React.FC<ScoreboardProps> = ({
  scores,
  currentPlayerId,
  currentDrawerId,
}) => {
  const sortedScores = [...scores].sort((a, b) => b.score - a.score);

  return (
    <div className="glass-panel-game w-full h-full flex flex-col overflow-hidden select-none">
      <div className="bg-indigo-950/60 p-2.5 text-center font-extrabold text-xs tracking-wider uppercase border-b border-white/20 flex items-center justify-center gap-1.5 shrink-0">
        <span>🏆</span> Bảng Xếp Hạng
      </div>

      <div className="flex-1 overflow-y-auto p-2 space-y-2 custom-scrollbar">
        {sortedScores.map((s, index) => {
          const isCurrent = s.playerId === currentPlayerId;
          const isDrawer = s.playerId === currentDrawerId;
          const avatarColor = AVATAR_BG_COLORS[index % AVATAR_BG_COLORS.length];
          const initial = (s.username || 'P').charAt(0).toUpperCase();

          return (
            <div
              key={s.playerId}
              className={`flex items-center gap-2.5 p-2 rounded-2xl border transition-all ${
                isDrawer
                  ? 'bg-white/30 border-amber-300 shadow-md ring-1 ring-amber-300/60'
                  : isCurrent
                  ? 'bg-white/20 border-white/40 shadow-sm'
                  : 'bg-white/10 border-white/15 hover:bg-white/15'
              }`}
            >
              {/* Avatar with status icon */}
              <div className="relative shrink-0">
                <div
                  className={`w-8 h-8 sm:w-9 sm:h-9 rounded-full flex items-center justify-center font-black text-xs sm:text-sm border-2 border-white/60 shadow-sm ${avatarColor}`}
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
                  <span className="font-extrabold text-xs text-white truncate">
                    {s.username}
                  </span>
                  {isCurrent && (
                    <span className="text-[9px] font-black text-sky-200 bg-sky-500/40 px-1 rounded">
                      Bạn
                    </span>
                  )}
                </div>
                <div className="text-[11px] font-black text-amber-300">
                  {s.score} <span className="text-[9px] font-semibold text-white/70">điểm</span>
                </div>
              </div>

              {/* Rank Badge */}
              <div className="shrink-0 text-right">
                {index === 0 && <span className="text-sm">🥇</span>}
                {index === 1 && <span className="text-sm">🥈</span>}
                {index === 2 && <span className="text-sm">🥉</span>}
                {index > 2 && (
                  <span className="text-[10px] font-black text-white/50 px-1">
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

