import React, { useState } from 'react';
import { usePlayerStore, playerStore } from '../store/playerStore';
import { CreateRoomForm } from '../features/room/CreateRoomForm';
import { JoinRoomForm } from '../features/room/JoinRoomForm';
import { ConnectionStatus } from '../components/ConnectionStatus';

export const HomePage: React.FC = () => {
  const { username } = usePlayerStore((s) => s);
  const [inputName, setInputName] = useState(username);
  const [activeTab, setActiveTab] = useState<'create' | 'join'>('create');

  const handleNameBlur = () => {
    if (inputName.trim()) {
      playerStore.setPlayer(inputName.trim());
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col justify-between p-4 md:p-8 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-indigo-900/20 via-slate-950 to-slate-950">
      {/* Top Navbar */}
      <header className="max-w-5xl w-full mx-auto flex items-center justify-between py-4 border-b border-slate-800/80">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center text-xl font-black shadow-lg shadow-indigo-500/30">
            🎨
          </div>
          <div>
            <h1 className="font-extrabold text-lg tracking-tight bg-gradient-to-r from-white via-slate-200 to-indigo-300 bg-clip-text text-transparent">
              Doodle Guess
            </h1>
            <p className="text-xs text-slate-400">Multiplayer Drawing & Guessing Game</p>
          </div>
        </div>
        <ConnectionStatus />
      </header>

      {/* Main Content */}
      <main className="max-w-md w-full mx-auto my-8 space-y-6">
        {/* Profile Card */}
        <div className="bg-slate-900/80 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-3">
          <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">
            Your Player Name
          </label>
          <div className="relative">
            <input
              type="text"
              placeholder="Enter your nickname..."
              value={inputName}
              onChange={(e) => {
                setInputName(e.target.value);
                playerStore.setPlayer(e.target.value);
              }}
              onBlur={handleNameBlur}
              className="w-full px-4 py-3 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 font-semibold"
            />
            <span className="absolute right-3 top-3 text-sm text-slate-500">✏️</span>
          </div>
        </div>

        {/* Action Tabs */}
        <div className="bg-slate-900/40 p-1.5 rounded-2xl border border-slate-800 flex gap-2">
          <button
            onClick={() => setActiveTab('create')}
            className={`flex-1 py-2.5 rounded-xl font-bold text-sm transition-all ${
              activeTab === 'create'
                ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Create Room
          </button>
          <button
            onClick={() => setActiveTab('join')}
            className={`flex-1 py-2.5 rounded-xl font-bold text-sm transition-all ${
              activeTab === 'join'
                ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Join Room
          </button>
        </div>

        {/* Forms */}
        {activeTab === 'create' ? <CreateRoomForm /> : <JoinRoomForm />}
      </main>

      {/* Footer */}
      <footer className="text-center text-xs text-slate-600 py-4 border-t border-slate-900">
        Multiplayer Drawing & Guessing Game MVP • Built with React & TypeScript
      </footer>
    </div>
  );
};
