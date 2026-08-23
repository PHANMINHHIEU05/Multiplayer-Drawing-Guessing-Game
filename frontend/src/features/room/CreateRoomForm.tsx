import React, { useState } from 'react';
import { wsClient } from '../../websocket/WebSocketClient';
import { MessageType } from '../../websocket/protocol';
import { usePlayerStore } from '../../store/playerStore';

interface CreateRoomFormProps {
  onSuccess?: () => void;
}

export const CreateRoomForm: React.FC<CreateRoomFormProps> = ({ onSuccess }) => {
  const { playerId, username } = usePlayerStore((s) => s);
  const [maxPlayers, setMaxPlayers] = useState<number>(4);
  const [totalRounds, setTotalRounds] = useState<number>(5);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) {
      setError('Please set a username first.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await wsClient.send(MessageType.CREATE_ROOM, {
        playerId,
        username,
        roomName: `${username}'s Room`,
        maxPlayers,
        totalRounds,
      });
      if (onSuccess) onSuccess();
    } catch (err: any) {
      setError(err.message || 'Failed to create room');
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleCreate} className="space-y-4 bg-slate-900/80 border border-slate-800 p-6 rounded-2xl shadow-xl">
      <h2 className="text-xl font-bold text-slate-100 flex items-center gap-2">
        <span>➕</span> Create New Room
      </h2>

      {error && <div className="p-3 bg-rose-500/20 border border-rose-500/30 text-rose-300 rounded-lg text-sm">{error}</div>}

      <div>
        <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
          Max Players ({maxPlayers})
        </label>
        <input
          type="range"
          min="2"
          max="10"
          value={maxPlayers}
          onChange={(e) => setMaxPlayers(parseInt(e.target.value))}
          className="w-full accent-indigo-500 bg-slate-800 rounded-lg cursor-pointer"
        />
        <div className="flex justify-between text-xs text-slate-500 mt-1">
          <span>2 Players</span>
          <span>10 Players</span>
        </div>
      </div>

      <div>
        <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
          Total Rounds ({totalRounds})
        </label>
        <input
          type="range"
          min="3"
          max="10"
          value={totalRounds}
          onChange={(e) => setTotalRounds(parseInt(e.target.value))}
          className="w-full accent-indigo-500 bg-slate-800 rounded-lg cursor-pointer"
        />
        <div className="flex justify-between text-xs text-slate-500 mt-1">
          <span>3 Rounds</span>
          <span>10 Rounds</span>
        </div>
      </div>

      <button
        type="submit"
        disabled={loading}
        className="w-full py-3 bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-500 hover:to-violet-500 text-white font-semibold rounded-xl shadow-lg shadow-indigo-600/30 transition-all disabled:opacity-50"
      >
        {loading ? 'Creating...' : 'Create Room'}
      </button>
    </form>
  );
};
