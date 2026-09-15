import React, { useState } from 'react';
import { useRoomStore } from '../../store/roomStore';
import { usePlayerStore } from '../../store/playerStore';
import { wsClient } from '../../websocket/WebSocketClient';
import { MessageType } from '../../websocket/protocol';
import { PlayerList } from '../../components/PlayerList';
import { ChatPanel } from '../chat/ChatPanel';

/** TV10: readiness summary for the Start gating UI. Host is implicitly ready. */
function readinessSummary(room: { players: { playerId: string; ready?: boolean }[]; hostPlayerId: string }) {
  const required = room.players.filter((p) => p.playerId !== room.hostPlayerId);
  const unready = required.filter((p) => !p.ready);
  return { allReady: required.length > 0 ? unready.length === 0 : true, unreadyCount: unready.length, requiredCount: required.length };
}

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

  // TV10: ready toggle (non-host) — server is authoritative, UI reflects PLAYER_READY_CHANGED
  const myReady = room.players.find((p) => p.playerId === playerId)?.ready ?? false;
  const handleToggleReady = async () => {
    try {
      await wsClient.send(MessageType.SET_READY, { ready: !myReady });
    } catch (err: any) {
      setError(err.message || 'Không thể đổi trạng thái sẵn sàng');
    }
  };

  // TV10: host kick (WAITING-only) with a small confirmation
  const [kickTarget, setKickTarget] = useState<string | null>(null);
  const handleKick = async (targetPlayerId: string) => {
    setKickTarget(null);
    try {
      await wsClient.send(MessageType.KICK_PLAYER, { targetPlayerId });
    } catch (err: any) {
      setError(err.message || 'Không thể mời người chơi khỏi phòng');
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

            {/* TV10: non-host Ready toggle */}
            {!isHost && (
              <button
                onClick={handleToggleReady}
                className={`bouncy-btn px-5 py-2.5 font-black text-xs rounded-2xl transition-all border ${
                  myReady
                    ? 'bg-emerald-500 hover:bg-emerald-600 text-white border-emerald-400 shadow-[0_4px_0_0_#059669]'
                    : 'bg-white/80 hover:bg-white text-emerald-700 border-emerald-300 shadow-sm'
                }`}
              >
                {myReady ? '✓ Đã sẵn sàng' : 'Sẵn sàng'}
              </button>
            )}

            {isHost && (() => {
              const { allReady, unreadyCount } = readinessSummary(room);
              const canStart = room.players.length >= 2 && allReady;
              return (
                <div className="flex flex-col items-end gap-1">
                  <button
                    onClick={handleStartGame}
                    disabled={starting || !canStart}
                    title={canStart ? '' : 'Cần ít nhất 2 người và tất cả sẵn sàng'}
                    className="bouncy-btn px-6 py-2.5 bg-emerald-500 hover:bg-emerald-600 text-white font-black text-sm rounded-2xl shadow-[0_4px_0_0_#059669] transition-all disabled:opacity-50 flex items-center gap-1.5"
                  >
                    <span>🚀</span>
                    <span>{starting ? 'Đang bắt đầu...' : 'BẮT ĐẦU GAME'}</span>
                  </button>
                  {!allReady && (
                    <span className="text-[10px] font-bold text-amber-600">
                      {unreadyCount === 1 ? 'Đang chờ 1 người sẵn sàng' : `Đang chờ ${unreadyCount} người sẵn sàng`}
                    </span>
                  )}
                  {allReady && room.players.length < 2 && (
                    <span className="text-[10px] font-bold text-amber-600">Cần ít nhất 2 người để bắt đầu</span>
                  )}
                </div>
              );
            })()}
          </div>
        </div>

        {error && (
          <div className="p-3 bg-rose-500/20 border border-rose-500/40 text-rose-800 rounded-2xl text-xs font-bold">
            ⚠️ {error}
          </div>
        )}

        <PlayerList players={room.players} hostPlayerId={room.hostPlayerId} currentPlayerId={playerId} />

        {/* TV10: host kick controls (WAITING-only) */}
        {isHost && room.status === 'WAITING' && (
          <div className="glass-panel-game p-3 shadow-lg select-none">
            <h4 className="text-[10px] font-black text-slate-700 uppercase tracking-wider mb-2 flex items-center gap-1.5">
              <span>🛡️</span> Quản lý phòng (Chủ phòng)
            </h4>
            <div className="flex flex-wrap gap-1.5">
              {room.players
                .filter((p) => p.playerId !== room.hostPlayerId)
                .map((p) => (
                  <div key={p.playerId} className="flex items-center gap-1.5 bg-white/70 border border-slate-200 rounded-xl px-2 py-1">
                    <span className="text-xs font-bold text-slate-700">{p.username}</span>
                    <span className={`text-[9px] font-black px-1.5 py-0.5 rounded-md ${p.ready ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                      {p.ready ? '✓ Sẵn sàng' : 'Chưa'}
                    </span>
                    {kickTarget === p.playerId ? (
                      <span className="flex items-center gap-1">
                        <button
                          onClick={() => handleKick(p.playerId)}
                          className="text-[10px] font-black text-rose-600 hover:text-rose-700 underline"
                        >
                          Xác nhận
                        </button>
                        <button
                          onClick={() => setKickTarget(null)}
                          className="text-[10px] font-bold text-slate-400 hover:text-slate-600 underline"
                        >
                          Hủy
                        </button>
                      </span>
                    ) : (
                      <button
                        onClick={() => setKickTarget(p.playerId)}
                        className="text-[10px] font-bold text-rose-500 hover:text-rose-600 hover:underline"
                        title={`Mời ${p.username} khỏi phòng`}
                      >
                        Mời ra
                      </button>
                    )}
                  </div>
                ))}
              {room.players.length <= 1 && (
                <span className="text-xs text-slate-400 italic">Chỉ có chủ phòng trong phòng.</span>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Right Column: Chat Panel */}
      <div className="lg:col-span-1 h-[480px]">
        <ChatPanel roomId={room.roomId} />
      </div>
    </div>
  );
};

