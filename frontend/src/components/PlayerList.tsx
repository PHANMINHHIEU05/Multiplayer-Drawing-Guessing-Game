import React from 'react';
import { Player } from '../types/room';

interface PlayerListProps {
  players: Player[];
  hostPlayerId: string;
  currentPlayerId: string;
  drawerId?: string;
}

export const PlayerList: React.FC<PlayerListProps> = ({
  players,
  hostPlayerId,
  currentPlayerId,
  drawerId,
}) => {
  return (
    <div className="bg-slate-900/60 backdrop-blur-md border border-slate-800 rounded-xl p-4 shadow-lg">
      <div className="flex justify-between items-center mb-3">
        <h3 className="text-sm font-semibold text-slate-300 uppercase tracking-wider">
          Players ({players.length})
        </h3>
      </div>
      <div className="space-y-2 max-h-60 overflow-y-auto pr-1 custom-scrollbar">
        {players.map((player) => {
          const isCurrent = player.playerId === currentPlayerId;
          const isHost = player.playerId === hostPlayerId;
          const isDrawer = player.playerId === drawerId;

          return (
            <div
              key={player.playerId}
              className={`flex items-center justify-between px-3 py-2 rounded-lg border transition-all ${
                isCurrent
                  ? 'bg-indigo-950/40 border-indigo-500/40 text-indigo-200'
                  : 'bg-slate-800/40 border-slate-700/50 text-slate-300'
              }`}
            >
              <div className="flex items-center gap-2.5">
                <div
                  className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold ${
                    isDrawer
                      ? 'bg-amber-500 text-slate-950 ring-2 ring-amber-400/50'
                      : 'bg-slate-700 text-slate-200'
                  }`}
                >
                  {player.username.charAt(0).toUpperCase()}
                </div>
                <div>
                  <div className="text-sm font-medium flex items-center gap-1.5">
                    {player.username}
                    {isCurrent && <span className="text-[10px] bg-indigo-500/20 text-indigo-300 px-1.5 py-0.5 rounded border border-indigo-500/30">You</span>}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-1.5">
                {isDrawer && (
                  <span className="text-xs bg-amber-500/20 text-amber-300 px-2 py-0.5 rounded-md border border-amber-500/30 font-medium">
                    ✏️ Drawer
                  </span>
                )}
                {isHost && (
                  <span className="text-xs bg-purple-500/20 text-purple-300 px-2 py-0.5 rounded-md border border-purple-500/30 font-medium">
                    👑 Host
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
