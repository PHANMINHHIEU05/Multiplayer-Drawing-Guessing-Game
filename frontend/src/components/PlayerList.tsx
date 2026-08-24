import React from 'react';
import { Player } from '../types/room';

interface PlayerListProps {
  players: Player[];
  hostPlayerId: string;
  currentPlayerId: string;
  drawerId?: string;
}

const AVATAR_BG_COLORS = [
  'bg-amber-200 text-amber-800',
  'bg-sky-200 text-sky-800',
  'bg-emerald-200 text-emerald-800',
  'bg-purple-200 text-purple-800',
  'bg-pink-200 text-pink-800',
  'bg-indigo-200 text-indigo-800',
];

export const PlayerList: React.FC<PlayerListProps> = ({
  players,
  hostPlayerId,
  currentPlayerId,
  drawerId,
}) => {
  return (
    <div className="glass-panel-game p-4 shadow-lg select-none">
      <div className="flex justify-between items-center mb-3">
        <h3 className="text-xs font-black text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
          <span>👥</span> Người chơi trong phòng ({players.length})
        </h3>
      </div>
      <div className="space-y-2 max-h-64 overflow-y-auto pr-1 custom-scrollbar">
        {players.map((player, idx) => {
          const isCurrent = player.playerId === currentPlayerId;
          const isHost = player.playerId === hostPlayerId;
          const isDrawer = player.playerId === drawerId;
          const avatarColor = AVATAR_BG_COLORS[idx % AVATAR_BG_COLORS.length];

          return (
            <div
              key={player.playerId}
              className={`flex items-center justify-between px-3 py-2.5 rounded-2xl border transition-all ${
                isCurrent
                  ? 'bg-sky-100/90 border-primary text-slate-800 shadow-sm'
                  : 'bg-white/80 border-slate-200 text-slate-700 hover:bg-white'
              }`}
            >
              <div className="flex items-center gap-2.5">
                <div
                  className={`w-9 h-9 rounded-full flex items-center justify-center text-xs font-black border-2 border-white shadow-sm ${avatarColor}`}
                >
                  {player.username.charAt(0).toUpperCase()}
                </div>
                <div>
                  <div className="text-xs font-extrabold flex items-center gap-1.5 text-slate-800">
                    {player.username}
                    {isCurrent && (
                      <span className="text-[9px] bg-primary text-white font-black px-1.5 py-0.5 rounded-md">
                        Bạn
                      </span>
                    )}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-1.5">
                {isDrawer && (
                  <span className="text-[10px] bg-amber-100 text-amber-800 px-2 py-0.5 rounded-lg border border-amber-300 font-extrabold flex items-center gap-1">
                    ✏️ Người vẽ
                  </span>
                )}
                {isHost && (
                  <span className="text-[10px] bg-purple-100 text-purple-800 px-2 py-0.5 rounded-lg border border-purple-300 font-extrabold flex items-center gap-1">
                    👑 Chủ phòng
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

