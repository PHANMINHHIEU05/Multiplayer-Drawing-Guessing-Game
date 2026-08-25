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
      setError(err.message || 'Không thể bắt đầu game');
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
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 max-w-6xl mx-auto p-4">
      {/* Left Column: Room info & Player List */}
      <div className="lg:col-span-2 space-y-4">
        <div className="glass-panel rounded-3xl p-5 shadow-2xl flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs px-3 py-1 rounded-xl bg-primary text-white font-mono font-black shadow-sm">
                #{room.roomId}
              </span>
              <span className="text-xs px-2.5 py-1 rounded-xl bg-sky-100 text-sky-800 font-extrabold">
                {room.playerCount} / {room.maxPlayers} Người
              </span>
            </div>
            <h1 className="text-2xl font-black text-slate-800 mt-2">
              {room.name || `Phòng #${room.roomId}`}
            </h1>
            <p className="text-xs text-slate-500 font-bold mt-1">
              Số vòng: <span className="text-primary font-black">{room.roundCount}</span> • Thời gian vẽ:{' '}
              <span className="text-primary font-black">{room.roundDuration}s</span>
            </p>
          </div>

          <div className="flex items-center gap-2.5">
            <button
              onClick={handleLeaveRoom}
              disabled={leaving}
              className="bouncy-btn px-4 py-2.5 bg-white/80 hover:bg-white text-rose-600 border border-rose-200 font-extrabold text-xs rounded-2xl transition-all shadow-sm"
            >
              {leaving ? 'Đang rời...' : 'Rời phòng'}
            </button>

            {isHost && (
              <button
                onClick={handleStartGame}
                disabled={starting || room.players.length < 1}
                className="bouncy-btn px-6 py-2.5 bg-emerald-500 hover:bg-emerald-600 text-white font-black text-sm rounded-2xl shadow-[0_4px_0_0_#059669] transition-all disabled:opacity-50 flex items-center gap-1.5"
              >
                <span>🚀</span>
                <span>{starting ? 'Đang bắt đầu...' : 'BẮT ĐẦU GAME'}</span>
              </button>
            )}
          </div>
        </div>

        {error && (
          <div className="p-3 bg-rose-500/20 border border-rose-500/40 text-rose-800 rounded-2xl text-xs font-bold">
            ⚠️ {error}
          </div>
        )}

        <PlayerList players={room.players} hostPlayerId={room.hostPlayerId} currentPlayerId={playerId} />
      </div>

      {/* Right Column: Chat Panel */}
      <div className="lg:col-span-1 h-[480px]">
        <ChatPanel roomId={room.roomId} />
      </div>
    </div>
  );
};

