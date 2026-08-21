import React from 'react';
import { PlayerScore } from '../types/game';

interface ScoreboardProps {
  scores: PlayerScore[];
  currentPlayerId: string;
}

export const Scoreboard: React.FC<ScoreboardProps> = ({ scores, currentPlayerId }) => {
  const sortedScores = [...scores].sort((a, b) => b.score - a.score);

  return (
    <div className="bg-slate-900/60 backdrop-blur-md border border-slate-800 rounded-xl p-4 shadow-lg">
      <h3 className="text-sm font-semibold text-slate-300 uppercase tracking-wider mb-3 flex items-center gap-2">
        <span>🏆</span> Leaderboard
      </h3>
      <div className="space-y-2">
        {sortedScores.map((s, index) => {
          const isCurrent = s.playerId === currentPlayerId;
          return (
            <div
              key={s.playerId}
              className={`flex items-center justify-between px-3 py-2 rounded-lg border transition-all ${
                isCurrent
                  ? 'bg-indigo-950/40 border-indigo-500/40 text-indigo-200'
                  : 'bg-slate-800/40 border-slate-700/50 text-slate-300'
              }`}
            >
              <div className="flex items-center gap-3">
                <span
                  className={`w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold ${
                    index === 0
                      ? 'bg-amber-400 text-slate-950'
                      : index === 1
                      ? 'bg-slate-300 text-slate-950'
                      : index === 2
                      ? 'bg-amber-700 text-white'
                      : 'text-slate-500'
                  }`}
                >
                  {index + 1}
                </span>
                <span className="text-sm font-medium">
                  {s.username} {isCurrent && <span className="text-[10px] text-indigo-400">(You)</span>}
                </span>
              </div>
              <div className="flex items-center gap-2">
                {s.hasGuessed && (
                  <span className="text-xs bg-emerald-500/20 text-emerald-300 px-1.5 py-0.5 rounded border border-emerald-500/30">
                    ✓ Guessed
                  </span>
                )}
                <span className="text-sm font-bold text-amber-400">{s.score} pts</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
