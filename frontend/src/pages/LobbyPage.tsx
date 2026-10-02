import React from 'react';
import { RoomLobby } from '../features/room/RoomLobby';
import { ConnectionStatus } from '../components/ConnectionStatus';

export const LobbyPage: React.FC = () => {
  return (
    <div className="dg-page min-h-screen flex flex-col justify-between py-3 sm:py-4 text-slate-800">
      <header className="dg-topbar flex items-center justify-between gap-3 mb-7">
        <div className="dg-brand">
          <span className="dg-brand-palette">🎨</span>
          <span className="dg-brand-name text-2xl sm:text-3xl">
            Dopamine<span className="dg-brand-dot">.io</span>
          </span>
        </div>
        <ConnectionStatus />
      </header>

      <main className="dg-lobby-shell glass-panel flex-1">
        <RoomLobby />
      </main>

      <footer className="text-center text-xs font-bold text-white/90 pt-5 pb-2 px-4 drop-shadow">
        Chuẩn bị sẵn sàng — trận đấu sẽ bắt đầu khi chủ phòng nhấn chơi!
      </footer>
    </div>
  );
};
