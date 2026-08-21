import React from 'react';
import { RoomLobby } from '../features/room/RoomLobby';
import { ConnectionStatus } from '../components/ConnectionStatus';

export const LobbyPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col justify-between p-4 md:p-8 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-indigo-900/20 via-slate-950 to-slate-950">
      <header className="max-w-6xl w-full mx-auto flex items-center justify-between py-4 border-b border-slate-800/80 mb-6">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center text-xl font-black shadow-lg shadow-indigo-500/30">
            🎨
          </div>
          <h1 className="font-extrabold text-lg tracking-tight bg-gradient-to-r from-white via-slate-200 to-indigo-300 bg-clip-text text-transparent">
            Room Lobby
          </h1>
        </div>
        <ConnectionStatus />
      </header>

      <main className="flex-1">
        <RoomLobby />
      </main>

      <footer className="text-center text-xs text-slate-600 py-4 mt-8 border-t border-slate-900">
        Waiting for host to start the game...
      </footer>
    </div>
  );
};
