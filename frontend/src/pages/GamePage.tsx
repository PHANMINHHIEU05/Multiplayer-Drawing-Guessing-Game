import React, { useEffect, useCallback, useState, useRef } from 'react';
import { useGameStore, gameStore } from '../store/gameStore';
import { usePlayerStore, playerStore } from '../store/playerStore';
import { useRoomStore, roomStore } from '../store/roomStore';
import { GameHeader } from '../features/game/GameHeader';
import { DrawingCanvas, DrawingCanvasHandle } from '../features/drawing/DrawingCanvas';
import { DrawingToolbar } from '../features/drawing/DrawingToolbar';
import { Scoreboard } from '../components/Scoreboard';
import { ChatPanel } from '../features/chat/ChatPanel';
import { GuessInput } from '../features/game/GuessInput';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { NetworkInspector } from '../components/NetworkInspector';
import { wsClient } from '../websocket/WebSocketClient';
import { MessageType, createWSRequest } from '../websocket/protocol';
import { DrawPoint, RemoteStrokeState } from '../types/game';
import { metricsStore } from '../store/metricsStore';
import { recoveryStore, useRecoveryStore } from '../store/recoveryStore';
import { useConnectionStore } from '../store/connectionStore';
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
  const remoteStrokeMapRef = useRef<Map<string, RemoteStrokeState>>(new Map());

  const roomId = room?.roomId || gameState?.roomId || '';
  const isDrawer = gameState?.drawerId === playerId;
  const currentRound = gameState?.currentRound || 1;
  // C9: lock guess input after this player has guessed correctly in the current round
  const hasGuessed = !!gameState?.scores?.find((s) => s.playerId === playerId)?.hasGuessed;
  // TV7: reconnect/recovery UX states (small banner, game stays visible)
  const canvasMode = useRecoveryStore((s) => s.mode);
  const connStatus = useConnectionStore((s) => s.status);
  const showRecoveryBanner = canvasMode === 'RECOVERING';
  const showReconnectBanner =
    connStatus === 'DISCONNECTED' || connStatus === 'RECONNECTING' || connStatus === 'CONNECTING' || connStatus === 'FAILING_OVER';
  // TV10: rematch is host-only; room.status WAITING (after ROOM_RESET) exits game screen via App routing
  const isHost = room?.hostPlayerId === playerId;
  const handleRematch = async () => {
    try {
      await wsClient.send('REMATCH', {});
    } catch (err: any) {
      console.error('Rematch failed:', err);
    }
  };

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
    const unsubscribeBinary = wsClient.addBinaryListener((buffer) => {
      const decoded = decodeDrawingFrame(buffer);
      if (!decoded) return;

      // TV7 recovery/live race: while canvas recovery is in flight, buffer live
      // frames instead of applying them. They are flushed (in arrival order)
      // right after the recovered history is applied — no missing/duplicate stroke.
      if (recoveryStore.getState().mode === 'RECOVERING') {
        if (decoded.type === 'CLEAR_CANVAS') {
          recoveryStore.clearBuffer();
          return;
        }
        if (decoded.type === 'DRAW_START') {
          const isEraser = decoded.data.tool === 'ERASER' || decoded.data.colorHex.toUpperCase() === '#FFFFFF';
          recoveryStore.buffer([{
            x: decoded.data.x,
            y: decoded.data.y,
            color: decoded.data.colorHex,
            size: decoded.data.width,
            tool: isEraser ? 'ERASER' : 'BRUSH',
            strokeId: decoded.data.strokeId,
            isNewPath: true,
          }]);
          // remember stroke style for buffered batches of the same stroke
          remoteStrokeMapRef.current.set(decoded.data.strokeId, {
            strokeId: decoded.data.strokeId,
            tool: isEraser ? 'ERASER' : 'BRUSH',
            color: decoded.data.colorHex,
            width: decoded.data.width,
            round: decoded.data.round,
            lastX: decoded.data.x,
            lastY: decoded.data.y,
          });
        } else if (decoded.type === 'DRAW_BATCH') {
          const strokeState = remoteStrokeMapRef.current.get(decoded.data.strokeId);
          const color = strokeState?.color ?? '#000000';
          const size = strokeState?.width ?? 4;
          const tool = strokeState?.tool ?? 'BRUSH';
          recoveryStore.buffer(decoded.data.points.map((p) => ({
            x: p.x,
            y: p.y,
            color,
            size,
            tool,
            strokeId: decoded.data.strokeId,
            isNewPath: false,
          })));
        }
        return;
      }

      switch (decoded.type) {
        case 'DRAW_START': {
          metricsStore.recordDrawBatchReceived(1, decoded.data.strokeId, 0);
          const isEraser = decoded.data.tool === 'ERASER' || decoded.data.colorHex.toUpperCase() === '#FFFFFF';
          const strokeState: RemoteStrokeState = {
            strokeId: decoded.data.strokeId,
            tool: isEraser ? 'ERASER' : 'BRUSH',
            color: decoded.data.colorHex,
            width: decoded.data.width,
            round: decoded.data.round,
            lastX: decoded.data.x,
            lastY: decoded.data.y,
          };
          remoteStrokeMapRef.current.set(decoded.data.strokeId, strokeState);

          gameStore.addDrawPoint({
            x: decoded.data.x,
            y: decoded.data.y,
            color: strokeState.color,
            size: strokeState.width,
            tool: strokeState.tool,
            strokeId: strokeState.strokeId,
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
          const strokeState = remoteStrokeMapRef.current.get(decoded.data.strokeId);
          const color = strokeState?.color ?? '#000000';
          const size = strokeState?.width ?? 4;
          const tool = strokeState?.tool ?? 'BRUSH';

          const batchPoints: DrawPoint[] = decoded.data.points.map((p) => ({
            x: p.x,
            y: p.y,
            color,
            size,
            tool,
            strokeId: decoded.data.strokeId,
            isNewPath: false,
          }));
          gameStore.addDrawPoints(batchPoints);
          break;
        }
        case 'DRAW_END': {
          metricsStore.resetStrokeSequence(decoded.data.strokeId);
          remoteStrokeMapRef.current.delete(decoded.data.strokeId);
          break;
        }
        case 'CLEAR_CANVAS': {
          metricsStore.resetStrokeSequence();
          remoteStrokeMapRef.current.clear();
          gameStore.clearDrawPoints();
          canvasHandleRef.current?.clear();
          break;
        }
      }
    });

    const unsubscribeMessage = wsClient.addMessageListener((msg) => {
      if (msg.type === MessageType.CANVAS_CLEARED || msg.type === 'CANVAS_CLEARED') {
        metricsStore.resetStrokeSequence();
        remoteStrokeMapRef.current.clear();
        gameStore.clearDrawPoints();
        canvasHandleRef.current?.clear();
      }
      // TV5: GAME_STARTED no longer carries the secret word (privacy fix), so the
      // drawer fetches fresh state immediately instead of waiting for the 5s poll.
      if (msg.type === MessageType.GAME_STARTED) {
        const rid =
          roomStore.getState().room?.roomId ||
          gameStore.getState().gameState?.roomId ||
          '';
        const pid = playerStore.getState().playerId;
        if (rid) {
          wsClient
            .send(MessageType.GET_GAME_STATE, { roomId: rid, playerId: pid }, 5000)
            .catch(() => {});
        }
      }
    });

    return () => {
      unsubscribeBinary();
      unsubscribeMessage();
    };
  }, []);

  // ─── Lifecycle: Round Transition (TV2-F04) ──────────────────────────
  const prevRoundRef = useRef<number>(currentRound);
  useEffect(() => {
    if (currentRound !== prevRoundRef.current) {
      console.log(`[GamePage] Round transitioned from ${prevRoundRef.current} to ${currentRound}. Resetting canvas state.`);
      prevRoundRef.current = currentRound;
      // Cancel active stroke if in progress
      canvasHandleRef.current?.cancelActiveStroke();
      canvasHandleRef.current?.clear();
      remoteStrokeMapRef.current.clear();
      gameStore.clearDrawPoints();
      currentStrokeIdRef.current = generateStrokeId();
      seqCounterRef.current = 0;
      metricsStore.resetStrokeSequence();
    }
  }, [currentRound]);

  // ─── Lifecycle: Drawer Transition (TV2-F05) ─────────────────────────
  const prevDrawerRef = useRef<boolean>(isDrawer);
  useEffect(() => {
    if (prevDrawerRef.current !== isDrawer) {
      prevDrawerRef.current = isDrawer;
      if (!isDrawer) {
        // Player lost drawer role - immediately cancel active stroke and flush buffers
        console.log('[GamePage] Drawer privilege revoked. Cancelling active drawing stroke.');
        canvasHandleRef.current?.cancelActiveStroke();
      }
    }
  }, [isDrawer]);

  // ─── Drawing Callbacks ─────────────────────────────────────────────

  /** Send a batch of draw points to the server via WebSocket according to active mode */
  const handleDrawBatch = useCallback((points: DrawPoint[]) => {
    if (!roomId || points.length === 0) return;

    const isEraserTool = activeTool === 'eraser';
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
          colorHex: isEraserTool ? '#FFFFFF' : (firstPt.color || brushColor),
          width: isEraserTool ? Math.min(64, Math.round((brushSize || 4) * 2.5)) : Math.min(64, Math.round(firstPt.size || brushSize)),
          tool: isEraserTool ? 'ERASER' : 'BRUSH',
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

    // JSON Modes: Include tool and strokeId in payload
    const pointsWithTool = points.map((p) => ({
      ...p,
      tool: isEraserTool ? ('ERASER' as const) : ('BRUSH' as const),
      strokeId: currentStrokeIdRef.current,
    }));

    if (mode === 'JSON_POINT') {
      for (const pt of pointsWithTool) {
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
    if (pointsWithTool.length === 1) {
      const req = createWSRequest(MessageType.DRAW_POINT, {
        roomId,
        drawerId: playerId,
        point: pointsWithTool[0],
      });
      wsClient.sendRaw(JSON.stringify(req));
      metricsStore.recordDrawBatchSent(1);
    } else {
      const req = createWSRequest(MessageType.DRAW_BATCH, {
        roomId,
        drawerId: playerId,
        points: pointsWithTool,
      });
      wsClient.sendRaw(JSON.stringify(req));
      metricsStore.recordDrawBatchSent(pointsWithTool.length);
    }
  }, [roomId, playerId, currentRound, brushColor, brushSize, activeTool]);

  /** Send clear canvas command to the server */
  const handleClearCanvas = useCallback(() => {
    if (!roomId) return;

    remoteStrokeMapRef.current.clear();
    gameStore.clearDrawPoints();
    canvasHandleRef.current?.clear();

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

  // TV5 regression fix: game-service reports "FINISHED" when the game ends
  // (legacy "GAME_OVER" kept for compatibility with older payloads).
  const isGameOver = gameState.status === 'GAME_OVER' || gameState.status === 'FINISHED';

  return (
    <div className="h-screen w-screen flex flex-col p-2 sm:p-3 md:p-4 gap-2 sm:gap-3 overflow-hidden text-slate-100 select-none">
      {/* TV7: reconnect / recovery banners — small, non-blocking */}
      {showReconnectBanner && (
        <div className="shrink-0 px-4 py-2 rounded-2xl bg-rose-500/25 border border-rose-300/50 backdrop-blur-md text-rose-100 text-xs font-bold flex items-center gap-2 animate-pulse">
          <span className="w-2 h-2 rounded-full bg-rose-400" />
          {connStatus === 'DISCONNECTED'
            ? 'Mất kết nối...'
            : connStatus === 'FAILING_OVER'
              ? 'Đang chuyển máy chủ...'
              : 'Đang kết nối lại...'}
        </div>
      )}
      {showRecoveryBanner && (
        <div className="shrink-0 px-4 py-2 rounded-2xl bg-amber-400/25 border border-amber-300/50 backdrop-blur-md text-amber-100 text-xs font-bold flex items-center gap-2 animate-pulse">
          <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
          Đang khôi phục ván chơi...
        </div>
      )}
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
            onClearCanvas={handleClearCanvas}
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
            {/* Floating NET chip / Network Inspector — anchored inside the canvas
                area so it never covers the chat panel or the guess input. */}
            <NetworkInspector />
          </div>

          {/* Bottom Dual Panels: Guessing Feed on Left, Social Chat on Right */}
          <div className="h-44 sm:h-48 grid grid-cols-1 md:grid-cols-2 gap-2 sm:gap-3 shrink-0">
            {/* Left: TRẢ LỜI / ĐOÁN TỪ */}
            <div className="h-full min-h-0">
              <GuessInput roomId={roomId} disabled={isDrawer || isGameOver} hasGuessed={hasGuessed} />
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

            {/* TV10: winner callout */}
            {(() => {
              const sorted = [...(gameState.scores || [])].sort((a, b) => b.score - a.score);
              const winner = sorted[0];
              return winner ? (
                <p className="text-sm font-black text-white">
                  🥇 Người thắng: <span className="text-amber-300">{winner.username || 'Người chơi'}</span> ({winner.score} điểm)
                </p>
              ) : null;
            })()}

            {isHost ? (
              <button
                onClick={handleRematch}
                className="bouncy-btn w-full py-3.5 bg-emerald-500 hover:bg-emerald-600 text-white font-black text-sm rounded-2xl shadow-[0_4px_0_0_#059669] transition-all"
              >
                CHƠI LẠI 🔁
              </button>
            ) : (
              <p className="text-xs font-bold text-slate-300 animate-pulse">
                Đang chờ chủ phòng bắt đầu ván mới...
              </p>
            )}

            <button
              onClick={() => {
                wsClient.send('LEAVE_ROOM', {}).catch(() => {});
                playerStore.clearSessionToken();
                roomStore.clearRoom();
                gameStore.clearGame();
              }}
              className="bouncy-btn w-full py-3 bg-white/15 hover:bg-white/25 text-white font-black text-sm rounded-2xl border border-white/25 transition-all"
            >
              RỜI PHÒNG 🚪
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

