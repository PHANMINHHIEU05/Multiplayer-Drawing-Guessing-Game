import React from 'react';
import { RoomLobby } from '../features/room/RoomLobby';
import { ConnectionStatus } from '../components/ConnectionStatus';

export const LobbyPage: React.FC = () => {
  return (
    <div className="min-h-screen flex flex-col justify-between p-4 md:p-6 text-slate-800">
      <header className="max-w-6xl w-full mx-auto flex items-center justify-between py-2 mb-4">
        <div className="flex items-center gap-3">
          <span className="text-3xl">🎨</span>
          <span className="bubbly-logo text-2xl font-black text-white drop-shadow-md">
            Dopamine
          </span>
        </div>
        <ConnectionStatus />
      </header>

      <main className="flex-1 max-w-6xl w-full mx-auto">
        <RoomLobby />
      </main>

      <footer className="text-center text-xs font-bold text-white/80 py-3 drop-shadow">
        Đang chờ chủ phòng bắt đầu trận đấu...
      </footer>
    </div>
  );
};

