import React, { useState } from 'react';
import { useRoomStore } from '../../store/roomStore';
import { usePlayerStore } from '../../store/playerStore';
import { wsClient } from '../../websocket/WebSocketClient';
import { MessageType } from '../../websocket/protocol';
import { PlayerList } from '../../components/PlayerList';
import { ChatPanel } from '../chat/ChatPanel';

export const RoomLobby: React.FC = () => {
  const room = useRoomStore((s) => s.room);
  const { playerId } = usePlayerStore((s) => s);
  const [starting, setStarting] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!room) return null;

  const isHost = room.hostPlayerId === playerId;

  const handleStartGame = async () => {
    setStarting(true);
    setError(null);
    try {
      await wsClient.send(MessageType.START_GAME, {
        roomId: room.roomId,
        playerId,
      });
    } catch (err: any) {
      setError(err.message || 'Failed to start game');
    } finally {
      setStarting(false);
    }
  };

  const handleLeaveRoom = async () => {
    setLeaving(true);
    try {
      await wsClient.send(MessageType.LEAVE_ROOM, {
        roomId: room.roomId,
        playerId,
      });
    } catch (err: any) {
      console.error('Leave room failed:', err);
    } finally {
      setLeaving(false);
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 max-w-6xl mx-auto p-4">
      <div className="lg:col-span-2 space-y-6">
        <div className="bg-slate-900/80 border border-slate-800 p-6 rounded-2xl shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs px-2.5 py-1 rounded-md bg-indigo-500/20 text-indigo-300 font-mono border border-indigo-500/30">
                {room.roomId}
              </span>
              <span className="text-xs px-2.5 py-1 rounded-md bg-slate-800 text-slate-400">
                {room.playerCount} / {room.maxPlayers} Players
              </span>
            </div>
            <h1 className="text-2xl font-bold text-slate-100 mt-2">{room.name || `Room ${room.roomId}`}</h1>
            <p className="text-sm text-slate-400">
              Rounds: <span className="text-indigo-300 font-semibold">{room.roundCount}</span> | Duration:{' '}
              <span className="text-indigo-300 font-semibold">{room.roundDuration}s</span>
            </p>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={handleLeaveRoom}
              disabled={leaving}
              className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-rose-400 hover:text-rose-300 border border-slate-700 font-medium rounded-xl transition-all"
            >
              {leaving ? 'Leaving...' : 'Leave Room'}
            </button>

            {isHost && (
              <button
                onClick={handleStartGame}
                disabled={starting || room.players.length < 1}
                className="px-6 py-2.5 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-bold rounded-xl shadow-lg shadow-emerald-600/30 transition-all disabled:opacity-50"
              >
                {starting ? 'Starting...' : '🚀 Start Game'}
              </button>
            )}
          </div>
        </div>

        {error && <div className="p-3 bg-rose-500/20 border border-rose-500/30 text-rose-300 rounded-xl text-sm">{error}</div>}

        <PlayerList players={room.players} hostPlayerId={room.hostPlayerId} currentPlayerId={playerId} />
      </div>

      <div className="lg:col-span-1">
        <ChatPanel roomId={room.roomId} />
      </div>
    </div>
  );
};
