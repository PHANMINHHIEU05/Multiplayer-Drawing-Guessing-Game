import React, { useState } from 'react';
import { wsClient } from '../../websocket/WebSocketClient';
import { MessageType } from '../../websocket/protocol';
import { usePlayerStore } from '../../store/playerStore';

interface JoinRoomFormProps {
  onSuccess?: () => void;
}

export const JoinRoomForm: React.FC<JoinRoomFormProps> = ({ onSuccess }) => {
  const { playerId, username } = usePlayerStore((s) => s);
  const [roomId, setRoomId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleJoin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) {
      setError('Please set a username first.');
      return;
    }
    if (!roomId.trim()) {
      setError('Please enter a Room ID.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await wsClient.send(MessageType.JOIN_ROOM, {
        roomId: roomId.trim(),
        playerId,
        username,
      });
      if (onSuccess) onSuccess();
    } catch (err: any) {
      setError(err.message || 'Failed to join room');
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleJoin} className="space-y-4 bg-slate-900/80 border border-slate-800 p-6 rounded-2xl shadow-xl">
      <h2 className="text-xl font-bold text-slate-100 flex items-center gap-2">
        <span>🚪</span> Join Room
      </h2>

      {error && <div className="p-3 bg-rose-500/20 border border-rose-500/30 text-rose-300 rounded-lg text-sm">{error}</div>}

      <div>
        <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Room ID</label>
        <input
          type="text"
          placeholder="e.g. room-1234"
          value={roomId}
          onChange={(e) => setRoomId(e.target.value)}
          className="w-full px-4 py-3 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 font-mono"
        />
      </div>

      <button
        type="submit"
        disabled={loading}
        className="w-full py-3 bg-slate-800 hover:bg-slate-700 text-slate-100 border border-slate-700 font-semibold rounded-xl transition-all disabled:opacity-50"
      >
        {loading ? 'Joining...' : 'Join Room'}
      </button>
    </form>
  );
};
