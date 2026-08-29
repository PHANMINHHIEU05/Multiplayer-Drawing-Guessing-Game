import React, { useEffect, useCallback, useState, useRef } from 'react';
import { useGameStore, gameStore } from '../store/gameStore';
import { usePlayerStore } from '../store/playerStore';
import { useRoomStore, roomStore } from '../store/roomStore';
import { GameHeader } from '../features/game/GameHeader';
import { DrawingCanvas, DrawingCanvasHandle } from '../features/drawing/DrawingCanvas';
import { DrawingToolbar } from '../features/drawing/DrawingToolbar';
import { Scoreboard } from '../components/Scoreboard';
import { ChatPanel } from '../features/chat/ChatPanel';
import { GuessInput } from '../features/game/GuessInput';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { wsClient } from '../websocket/WebSocketClient';
import { MessageType, createWSRequest } from '../websocket/protocol';
import { DrawPoint } from '../types/game';
import { metricsStore } from '../store/metricsStore';
import {
  encodeDrawStart,
  encodeDrawBatch,
  encodeClearCanvas,
  generateStrokeId,
  decodeDrawingFrame
} from '../features/drawing/binaryCodec';

export const GamePage: React.FC = () => {
  const gameState = useGameStore((s) => s.gameState);
  const drawPoints = useGameStore((s) => s.drawPoints);
  const room = useRoomStore((s) => s.room);
  const { playerId } = usePlayerStore((s) => s);

  // Drawing Toolbar State (for Drawer)
  const [brushColor, setBrushColor] = useState<string>('#000000');
  const [brushSize, setBrushSize] = useState<number>(4);
  const [activeTool, setActiveTool] = useState<'pen' | 'eraser' | 'fill' | 'line' | 'circle' | 'rect'>('pen');
  const canvasHandleRef = useRef<DrawingCanvasHandle | null>(null);

  // Stroke Tracking for Binary Mode
  const currentStrokeIdRef = useRef<string>(generateStrokeId());
  const seqCounterRef = useRef<number>(0);

  const roomId = room?.roomId || gameState?.roomId || '';
  const isDrawer = gameState?.drawerId === playerId;
  const currentRound = gameState?.currentRound || 1;

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

  // ─── Binary WebSocket Drawing Listener ──────────────────────────────
  useEffect(() => {
    // Listen for binary ArrayBuffer frames from WebSocket
    const unsubscribe = wsClient.addBinaryListener((buffer) => {
      const decoded = decodeDrawingFrame(buffer);
      if (!decoded) return;

      switch (decoded.type) {
        case 'DRAW_START': {
          metricsStore.recordDrawBatchReceived(1, decoded.data.strokeId, 0);
          gameStore.addDrawPoint({
            x: decoded.data.x,
            y: decoded.data.y,
            color: decoded.data.colorHex,
            size: decoded.data.width,
            isNewPath: true,
          });
          break;
        }
        case 'DRAW_BATCH': {
          metricsStore.recordDrawBatchReceived(
            decoded.data.points.length,
            decoded.data.strokeId,
            decoded.data.seqStart
          );
          const batchPoints: DrawPoint[] = decoded.data.points.map((p) => ({
            x: p.x,
            y: p.y,
            color: brushColor,
            size: brushSize,
            isNewPath: false,
          }));
          gameStore.addDrawPoints(batchPoints);
          break;
        }
        case 'DRAW_END': {
          metricsStore.resetStrokeSequence(decoded.data.strokeId);
          break;
        }
        case 'CLEAR_CANVAS': {
          metricsStore.resetStrokeSequence();
          gameStore.clearDrawPoints();
          break;
        }
      }
    });

    return unsubscribe;
  }, [brushColor, brushSize]);

  // ─── Drawing Callbacks ─────────────────────────────────────────────

  /** Send a batch of draw points to the server via WebSocket according to active mode */
  const handleDrawBatch = useCallback((points: DrawPoint[]) => {
    if (!roomId || points.length === 0) return;

    const mode = metricsStore.getState().drawingMode;

    if (mode === 'BINARY_BATCH') {
      const hasNewPath = points.some((p) => p.isNewPath);
      if (hasNewPath) {
        currentStrokeIdRef.current = generateStrokeId();
        seqCounterRef.current = 0;

        const firstPt = points[0];
        const startBuffer = encodeDrawStart({
          round: currentRound,
          strokeId: currentStrokeIdRef.current,
          x: firstPt.x,
          y: firstPt.y,
          colorHex: firstPt.color || brushColor,
          width: firstPt.size || brushSize,
        });
        wsClient.sendBinary(startBuffer);
        metricsStore.recordDrawBatchSent(1);

        if (points.length > 1) {
          const restPoints = points.slice(1);
          const batchBuffer = encodeDrawBatch({
            round: currentRound,
            strokeId: currentStrokeIdRef.current,
            seqStart: seqCounterRef.current,
            points: restPoints.map((p) => ({ x: p.x, y: p.y })),
          });
          seqCounterRef.current += restPoints.length;
          wsClient.sendBinary(batchBuffer);
          metricsStore.recordDrawBatchSent(restPoints.length);
        }
      } else {
        const batchBuffer = encodeDrawBatch({
          round: currentRound,
          strokeId: currentStrokeIdRef.current,
          seqStart: seqCounterRef.current,
          points: points.map((p) => ({ x: p.x, y: p.y })),
        });
        seqCounterRef.current += points.length;
        wsClient.sendBinary(batchBuffer);
        metricsStore.recordDrawBatchSent(points.length);
      }
      return;
    }

    if (mode === 'JSON_POINT') {
      for (const pt of points) {
        const req = createWSRequest(MessageType.DRAW_POINT, {
          roomId,
          drawerId: playerId,
          point: pt,
        });
        wsClient.sendRaw(JSON.stringify(req));
        metricsStore.recordDrawBatchSent(1);
      }
      return;
    }

    // Default: JSON_BATCH
    if (points.length === 1) {
      const req = createWSRequest(MessageType.DRAW_POINT, {
        roomId,
        drawerId: playerId,
        point: points[0],
      });
      wsClient.sendRaw(JSON.stringify(req));
      metricsStore.recordDrawBatchSent(1);
    } else {
      const req = createWSRequest(MessageType.DRAW_BATCH, {
        roomId,
        drawerId: playerId,
        points,
      });
      wsClient.sendRaw(JSON.stringify(req));
      metricsStore.recordDrawBatchSent(points.length);
    }
  }, [roomId, playerId, currentRound, brushColor, brushSize]);

  /** Send clear canvas command to the server */
  const handleClearCanvas = useCallback(() => {
    if (!roomId) return;

    const mode = metricsStore.getState().drawingMode;
    if (mode === 'BINARY_BATCH') {
      const buffer = encodeClearCanvas({ round: currentRound });
      wsClient.sendBinary(buffer);
      metricsStore.resetStrokeSequence();
    } else {
      const req = createWSRequest(MessageType.CLEAR_CANVAS, {
        roomId,
        drawerId: playerId,
        timestamp: Date.now(),
      });
      wsClient.sendRaw(JSON.stringify(req));
      metricsStore.resetStrokeSequence();
    }
  }, [roomId, playerId, currentRound]);

  if (!gameState) {
    return (
      <div className="min-h-screen flex items-center justify-center select-none">
        <div className="glass-panel p-8 rounded-3xl text-center space-y-4 shadow-2xl">
          <div className="w-12 h-12 border-4 border-white border-t-transparent rounded-full animate-spin mx-auto" />
          <p className="text-white font-extrabold text-sm drop-shadow">Đang tải trạng thái trận đấu...</p>
        </div>
      </div>
    );
  }

  const isGameOver = gameState.status === 'GAME_OVER';

  return (
    <div className="h-screen w-screen flex flex-col p-2 sm:p-3 md:p-4 gap-2 sm:gap-3 overflow-hidden text-slate-100 select-none">
      {/* Top Bar Header */}
      <div className="flex items-center gap-2 sm:gap-3 shrink-0">
        <div className="flex-1 min-w-0">
          <GameHeader gameState={gameState} isDrawer={isDrawer} roomId={roomId} />
        </div>
        <div className="hidden md:flex items-center gap-2">
          <button className="btn-3d bg-white/20 hover:bg-white/30 text-white p-2 rounded-2xl border border-white/30 shadow-md">
            <span className="material-symbols-outlined text-lg">volume_up</span>
          </button>
          <button className="btn-3d bg-white/20 hover:bg-white/30 text-white p-2 rounded-2xl border border-white/30 shadow-md">
            <span className="material-symbols-outlined text-lg">help</span>
          </button>
          <ConnectionStatus />
        </div>
      </div>

      {/* Main Game Arena Workspace */}
      <main className="flex-1 flex gap-2 sm:gap-3 min-h-0 relative">
        {/* Left Column 1: Leaderboard (Bảng Xếp Hạng) */}
        <div className="w-48 sm:w-56 h-full shrink-0">
          <Scoreboard
            scores={gameState.scores}
            currentPlayerId={playerId}
            currentDrawerId={gameState.drawerId}
          />
        </div>

        {/* Left Column 2: Vertical Drawing Toolbar (Drawer Only) */}
        {isDrawer && (
          <DrawingToolbar
            color={brushColor}
            size={brushSize}
            activeTool={activeTool}
            onColorChange={setBrushColor}
            onSizeChange={setBrushSize}
            onToolChange={setActiveTool}
            onClearCanvas={() => {
              canvasHandleRef.current?.clear();
              handleClearCanvas();
            }}
          />
        )}

        {/* Center & Bottom: Canvas + Dual Split Panels (Guess & Chat) */}
        <div className="flex-1 flex flex-col gap-2 sm:gap-3 min-w-0 h-full">
          {/* Main Drawing Canvas */}
          <div className="flex-1 min-h-0 relative">
            <DrawingCanvas
              ref={canvasHandleRef}
              isDrawer={isDrawer}
              color={brushColor}
              size={brushSize}
              isEraser={activeTool === 'eraser'}
              onDrawBatch={handleDrawBatch}
              onClearCanvas={handleClearCanvas}
              externalPoints={isDrawer ? undefined : drawPoints}
              hideInternalToolbar={true}
            />
          </div>

          {/* Bottom Dual Panels: Guessing Feed on Left, Social Chat on Right */}
          <div className="h-44 sm:h-48 grid grid-cols-1 md:grid-cols-2 gap-2 sm:gap-3 shrink-0">
            {/* Left: TRẢ LỜI / ĐOÁN TỪ */}
            <div className="h-full min-h-0">
              <GuessInput roomId={roomId} disabled={isDrawer || isGameOver} />
            </div>

            {/* Right: TRÒ CHUYỆN */}
            <div className="h-full min-h-0">
              <ChatPanel roomId={roomId} />
            </div>
          </div>
        </div>
      </main>

      {/* Game Over Celebration Modal */}
      {isGameOver && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4 z-50 animate-fadeIn">
          <div className="glass-panel-dark border-2 border-amber-400/80 rounded-3xl p-6 max-w-md w-full text-center space-y-5 shadow-2xl">
            <div className="text-6xl animate-bounce">🏆</div>
            <div>
              <h2 className="text-3xl font-black bubbly-logo text-amber-300">
                TRẬN ĐẤU KẾT THÚC!
              </h2>
              <p className="text-xs font-bold text-slate-300 mt-1">Bảng điểm chung cuộc</p>
            </div>

            <div className="max-h-52 overflow-y-auto">
              <Scoreboard
                scores={gameState.scores}
                currentPlayerId={playerId}
                currentDrawerId={gameState.drawerId}
              />
            </div>

            <button
              onClick={() => {
                if (room) {
                  roomStore.setRoom({ ...room, status: 'LOBBY' });
                }
              }}
              className="bouncy-btn w-full py-3.5 bg-primary hover:bg-primary-dark text-white font-black text-sm rounded-2xl shadow-[0_4px_0_0_#1565C0] transition-all"
            >
              TRỞ VỀ PHÒNG CHỜ 🚪
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

