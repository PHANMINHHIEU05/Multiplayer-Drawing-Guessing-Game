import React, { useEffect } from 'react';
import { useGameStore } from '../store/gameStore';
import { usePlayerStore } from '../store/playerStore';
import { useRoomStore, roomStore } from '../store/roomStore';
import { GameHeader } from '../features/game/GameHeader';
import { DrawingCanvas } from '../features/drawing/DrawingCanvas';
import { Scoreboard } from '../components/Scoreboard';
import { ChatPanel } from '../features/chat/ChatPanel';
import { GuessInput } from '../features/game/GuessInput';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { wsClient } from '../websocket/WebSocketClient';
import { MessageType } from '../websocket/protocol';

export const GamePage: React.FC = () => {
  const gameState = useGameStore((s) => s.gameState);
  const room = useRoomStore((s) => s.room);
  const { playerId } = usePlayerStore((s) => s);

  const roomId = room?.roomId || gameState?.roomId || '';
  const isDrawer = gameState?.drawerId === playerId;

  useEffect(() => {
    // Poll game state periodically if needed to keep state sync when game is active
    if (roomId && (gameState?.status === 'IN_ROUND' || room?.status === 'IN_GAME')) {
      const interval = setInterval(() => {
        wsClient
          .send(MessageType.GET_GAME_STATE, { roomId, playerId }, 5000)
          .catch(() => {});
      }, 5000);
      return () => clearInterval(interval);
    }
  }, [roomId, playerId, gameState?.status, room?.status]);

  if (!gameState) {
    return (
      <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center">
        <div className="text-center space-y-4">
          <div className="w-12 h-12 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin mx-auto" />
          <p className="text-slate-400">Loading game state...</p>
        </div>
      </div>
    );
  }

  const isGameOver = gameState.status === 'GAME_OVER';

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col justify-between p-4 md:p-6 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-indigo-900/20 via-slate-950 to-slate-950">
      {/* Header */}
      <header className="max-w-7xl w-full mx-auto flex items-center justify-between py-2 mb-4 border-b border-slate-800/80">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center text-lg font-black shadow-md shadow-indigo-500/30">
            🎨
          </div>
          <span className="font-extrabold text-sm tracking-tight text-slate-200">
            Room #{roomId}
          </span>
        </div>
        <ConnectionStatus />
      </header>

      {/* Main Game Grid */}
      <main className="max-w-7xl w-full mx-auto flex-1 grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* Left / Center Column: Drawing Canvas & Game Header */}
        <div className="lg:col-span-8 flex flex-col gap-4">
          <GameHeader gameState={gameState} isDrawer={isDrawer} />
          <div className="flex-1">
            <DrawingCanvas isDrawer={isDrawer} />
          </div>
          {/* Guess Input Bar under Canvas for guessers */}
          <div className="bg-slate-900/80 border border-slate-800 p-3 rounded-2xl shadow-xl">
            <GuessInput roomId={roomId} disabled={isDrawer || isGameOver} />
          </div>
        </div>

        {/* Right Column: Scoreboard & Chat */}
        <div className="lg:col-span-4 flex flex-col gap-4">
          <Scoreboard scores={gameState.scores} currentPlayerId={playerId} />
          <div className="flex-1">
            <ChatPanel roomId={roomId} />
          </div>
        </div>
      </main>

      {/* Game Over Modal Celebration */}
      {isGameOver && (
        <div className="fixed inset-0 bg-slate-950/90 backdrop-blur-lg flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl p-8 max-w-md w-full text-center space-y-6 shadow-2xl">
            <div className="text-6xl animate-bounce">🏆</div>
            <div>
              <h2 className="text-3xl font-black bg-gradient-to-r from-amber-300 to-yellow-500 bg-clip-text text-transparent">
                Game Over!
              </h2>
              <p className="text-sm text-slate-400 mt-1">Final Scoreboard</p>
            </div>

            <Scoreboard scores={gameState.scores} currentPlayerId={playerId} />

            <button
              onClick={() => {
                if (room) {
                  roomStore.setRoom({ ...room, status: 'LOBBY' });
                }
              }}
              className="w-full py-3 bg-indigo-600 hover:bg-indigo-500 text-white font-bold rounded-xl shadow-lg transition-all"
            >
              Return to Lobby
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
