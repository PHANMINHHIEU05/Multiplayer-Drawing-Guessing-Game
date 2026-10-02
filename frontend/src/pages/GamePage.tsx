import React, { useEffect, useCallback, useState, useRef } from "react";
import { useGameStore, gameStore } from "../store/gameStore";
import { usePlayerStore, playerStore } from "../store/playerStore";
import { useRoomStore } from "../store/roomStore";
import { GameHeader } from "../features/game/GameHeader";
import {
  DrawingCanvas,
  DrawingCanvasHandle,
} from "../features/drawing/DrawingCanvas";
import { DrawingToolbar } from "../features/drawing/DrawingToolbar";
import { Scoreboard } from "../components/Scoreboard";
import { ChatPanel } from "../features/chat/ChatPanel";
import { GuessInput } from "../features/game/GuessInput";
import {
  ReactionBar,
  RoundPhaseOverlay,
} from "../features/game/RoundPhaseOverlay";
import { ConnectionStatus } from "../components/ConnectionStatus";
import { NetworkInspector } from "../components/NetworkInspector";
import { wsClient, resetAllSessionState } from "../websocket/WebSocketClient";
import { MessageType, createWSRequest } from "../websocket/protocol";
import { DrawPoint, RemoteStrokeState } from "../types/game";
import { metricsStore } from "../store/metricsStore";
import { recoveryStore, useRecoveryStore } from "../store/recoveryStore";
import { useConnectionStore } from "../store/connectionStore";
import { reactionStore, useReactions } from "../store/reactionStore";
import {
  encodeDrawStart,
  encodeDrawBatch,
  encodeClearCanvas,
  generateStrokeId,
  decodeDrawingFrame,
} from "../features/drawing/binaryCodec";
import { SoundToggle } from "../components/SoundToggle";
import { audioManager } from "../audio/AudioManager";

export const GamePage: React.FC = () => {
  const gameState = useGameStore((s) => s.gameState);
  const drawPoints = useGameStore((s) => s.drawPoints);
  const room = useRoomStore((s) => s.room);
  const { playerId } = usePlayerStore((s) => s);

  // Drawing Toolbar State (for Drawer)
  const [brushColor, setBrushColor] = useState<string>("#000000");
  const [brushSize, setBrushSize] = useState<number>(4);
  const [activeTool, setActiveTool] = useState<
    "pen" | "eraser" | "fill" | "line" | "circle" | "rect"
  >("pen");
  const [showMobileScoreboard, setShowMobileScoreboard] = useState(false);
  const canvasHandleRef = useRef<DrawingCanvasHandle | null>(null);

  // Stroke Tracking for Binary Mode
  const currentStrokeIdRef = useRef<string>(generateStrokeId());
  const seqCounterRef = useRef<number>(0);
  const remoteStrokeMapRef = useRef<Map<string, RemoteStrokeState>>(new Map());

  const roomId = room?.roomId || gameState?.roomId || "";
  const isDrawer = gameState?.drawerId === playerId;
  const roundPhase = gameState?.roundPhase || "DRAWING";
  const canDraw = isDrawer && roundPhase === "DRAWING";
  const canGuess = roundPhase === "DRAWING";
  const reactions = useReactions();
  const currentRound = gameState?.currentRound || 1;
  // C9: lock guess input after this player has guessed correctly in the current round
  const hasGuessed = !!gameState?.scores?.find((s) => s.playerId === playerId)
    ?.hasGuessed;
  // TV7: reconnect/recovery UX states (small banner, game stays visible)
  const canvasMode = useRecoveryStore((s) => s.mode);
  const connStatus = useConnectionStore((s) => s.status);
  const showRecoveryBanner = canvasMode === "RECOVERING";
  const showReconnectBanner =
    connStatus === "DISCONNECTED" ||
    connStatus === "RECONNECTING" ||
    connStatus === "CONNECTING" ||
    connStatus === "FAILING_OVER";

  // BUG-3: Fallback timeout to prevent infinite spinner if gameState is never received
  const [loadTimedOut, setLoadTimedOut] = useState<boolean>(false);

  useEffect(() => {
    if (gameState) {
      setLoadTimedOut(false);
      return;
    }
    const timer = setTimeout(() => {
      if (!gameState) {
        setLoadTimedOut(true);
      }
    }, 6000);
    return () => clearTimeout(timer);
  }, [gameState]);

  // Initial fetch on mount if gameState is missing
  useEffect(() => {
    if (roomId && !gameState) {
      wsClient
        .send(MessageType.GET_GAME_STATE, { roomId, playerId }, 5000)
        .catch((err) => {
          const errStr = String(err?.message || err || "");
          if (
            errStr.includes("Game not found") ||
            errStr.includes("NOT_FOUND")
          ) {
            const current = gameStore.getState().gameState;
            if (current && current.status !== "FINISHED") {
              gameStore.setGameState({ ...current, status: "FINISHED" });
            }
          } else {
            console.warn("[GamePage] Initial GET_GAME_STATE failed:", err);
          }
        });
    }
  }, [roomId, playerId, !gameState]);

  useEffect(() => {
    // Poll game state periodically only when game is actively in round
    const isGameActive =
      (gameState?.status === "IN_ROUND" ||
        gameState?.status === "PLAYING" ||
        room?.status === "IN_GAME" ||
        room?.status === "PLAYING") &&
      gameState?.status !== "FINISHED" &&
      gameState?.status !== "GAME_OVER";
    if (roomId && isGameActive) {
      const interval = setInterval(() => {
        wsClient
          .send(MessageType.GET_GAME_STATE, { roomId, playerId }, 5000)
          .catch((err) => {
            const errStr = String(err?.message || err || "");
            if (
              errStr.includes("Game not found") ||
              errStr.includes("NOT_FOUND")
            ) {
              const current = gameStore.getState().gameState;
              if (current && current.status !== "FINISHED") {
                gameStore.setGameState({ ...current, status: "FINISHED" });
              }
            }
          });
      }, 5000);
      return () => clearInterval(interval);
    }
  }, [roomId, playerId, gameState?.status, room?.status]);

  // Clean exit when closing tab or navigating away
  useEffect(() => {
    const handleBeforeUnload = () => {
      if (roomId && playerId) {
        wsClient
          .send("LEAVE_ROOM", {
            roomId,
            playerId,
            username: playerStore.getState().username || "Người chơi",
          })
          .catch(() => {});
      }
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [roomId, playerId]);

  // ─── Binary WebSocket Drawing Listener ──────────────────────────────
  useEffect(() => {
    // Listen for binary ArrayBuffer frames from WebSocket
    const unsubscribeBinary = wsClient.addBinaryListener((buffer) => {
      const decoded = decodeDrawingFrame(buffer);
      if (!decoded) return;

      // TV7 recovery/live race: while canvas recovery is in flight, buffer live
      // frames instead of applying them. They are flushed (in arrival order)
      // right after the recovered history is applied — no missing/duplicate stroke.
      if (recoveryStore.getState().mode === "RECOVERING") {
        if (decoded.type === "CLEAR_CANVAS") {
          recoveryStore.clearBuffer();
          return;
        }
        if (decoded.type === "DRAW_START") {
          const isEraser = decoded.data.tool === "ERASER";
          recoveryStore.buffer([
            {
              x: decoded.data.x,
              y: decoded.data.y,
              color: decoded.data.colorHex,
              size: decoded.data.width,
              tool: isEraser ? "ERASER" : "BRUSH",
              strokeId: decoded.data.strokeId,
              isNewPath: true,
            },
          ]);
          // remember stroke style for buffered batches of the same stroke
          remoteStrokeMapRef.current.set(decoded.data.strokeId, {
            strokeId: decoded.data.strokeId,
            tool: isEraser ? "ERASER" : "BRUSH",
            color: decoded.data.colorHex,
            width: decoded.data.width,
            round: decoded.data.round,
            lastX: decoded.data.x,
            lastY: decoded.data.y,
          });
        } else if (decoded.type === "DRAW_BATCH") {
          const strokeState = remoteStrokeMapRef.current.get(
            decoded.data.strokeId,
          );
          const color = strokeState?.color ?? "#000000";
          const size = strokeState?.width ?? 4;
          const tool = strokeState?.tool ?? "BRUSH";
          recoveryStore.buffer(
            decoded.data.points.map((p) => ({
              x: p.x,
              y: p.y,
              color,
              size,
              tool,
              strokeId: decoded.data.strokeId,
              isNewPath: false,
            })),
          );
        }
        return;
      }

      switch (decoded.type) {
        case "DRAW_START": {
          metricsStore.recordDrawBatchReceived(1, decoded.data.strokeId, 0);
          const isEraser = decoded.data.tool === "ERASER";
          const strokeState: RemoteStrokeState = {
            strokeId: decoded.data.strokeId,
            tool: isEraser ? "ERASER" : "BRUSH",
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
        case "DRAW_BATCH": {
          metricsStore.recordDrawBatchReceived(
            decoded.data.points.length,
            decoded.data.strokeId,
            decoded.data.seqStart,
          );
          const strokeState = remoteStrokeMapRef.current.get(
            decoded.data.strokeId,
          );
          const color = strokeState?.color ?? "#000000";
          const size = strokeState?.width ?? 4;
          const tool = strokeState?.tool ?? "BRUSH";

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
        case "DRAW_END": {
          metricsStore.resetStrokeSequence(decoded.data.strokeId);
          remoteStrokeMapRef.current.delete(decoded.data.strokeId);
          break;
        }
        case "CLEAR_CANVAS": {
          metricsStore.resetStrokeSequence();
          remoteStrokeMapRef.current.clear();
          gameStore.clearDrawPoints();
          canvasHandleRef.current?.clear();
          break;
        }
      }
    });

    const unsubscribeMessage = wsClient.addMessageListener((msg) => {
      if (
        msg.type === MessageType.CANVAS_CLEARED ||
        msg.type === "CANVAS_CLEARED"
      ) {
        metricsStore.resetStrokeSequence();
        remoteStrokeMapRef.current.clear();
        gameStore.clearDrawPoints();
        canvasHandleRef.current?.clear();
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
        canvasHandleRef.current?.cancelActiveStroke();
      }
    }
  }, [isDrawer]);

  // ─── Drawing Callbacks ─────────────────────────────────────────────

  /** Send a batch of draw points to the server via WebSocket according to active mode */
  const handleDrawBatch = useCallback(
    (points: DrawPoint[]) => {
      if (
        !roomId ||
        points.length === 0 ||
        !isDrawer ||
        (gameStore.getState().gameState?.roundPhase &&
          gameStore.getState().gameState?.roundPhase !== "DRAWING")
      )
        return;

      const mode = metricsStore.getState().drawingMode;

      // Fill is a one-shot semantic operation. Binary drawing protocol v1 only
      // supports strokes, so fills use the authorized JSON drawing path in all modes.
      if (points.length === 1 && points[0].tool === "FILL") {
        const req = createWSRequest(MessageType.DRAW_POINT, {
          roomId,
          drawerId: playerId,
          point: points[0],
        });
        wsClient.sendRaw(JSON.stringify(req));
        metricsStore.recordDrawBatchSent(1);
        return;
      }

      if (mode === "BINARY_BATCH") {
        const hasNewPath = points.some((p) => p.isNewPath);
        if (hasNewPath) {
          currentStrokeIdRef.current = generateStrokeId();
          seqCounterRef.current = 0;

          const firstPt = points[0];
          const isEraserStroke = firstPt.tool === "ERASER";
          const startBuffer = encodeDrawStart({
            round: currentRound,
            strokeId: currentStrokeIdRef.current,
            x: firstPt.x,
            y: firstPt.y,
            colorHex: isEraserStroke ? "#FFFFFF" : firstPt.color || brushColor,
            width: Math.min(64, Math.round(firstPt.size || brushSize)),
            tool: isEraserStroke ? "ERASER" : "BRUSH",
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
        tool: p.tool === "ERASER" ? ("ERASER" as const) : ("BRUSH" as const),
        strokeId: currentStrokeIdRef.current,
      }));

      if (mode === "JSON_POINT") {
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
    },
    [
      roomId,
      playerId,
      currentRound,
      brushColor,
      brushSize,
      isDrawer,
    ],
  );

  /** Send clear canvas command to the server */
  const handleClearCanvas = useCallback(() => {
    if (
      !roomId ||
      !isDrawer ||
      (gameStore.getState().gameState?.roundPhase &&
        gameStore.getState().gameState?.roundPhase !== "DRAWING")
    )
      return;

    remoteStrokeMapRef.current.clear();
    gameStore.clearDrawPoints();
    canvasHandleRef.current?.clear();

    const mode = metricsStore.getState().drawingMode;
    if (mode === "BINARY_BATCH") {
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
  }, [roomId, playerId, currentRound, isDrawer]);

  const handleSelectWord = useCallback((choiceId: string) => {
    wsClient.send(MessageType.SELECT_WORD, { choiceId }, 5000).catch(() => {
      // The authoritative timeout remains active; a failed command cannot start drawing locally.
    });
  }, []);

  const [reactionOnCooldown, setReactionOnCooldown] = useState(false);
  const reactionCooldownUntilRef = useRef(0);
  const reactionCooldownTimerRef = useRef<number | null>(null);

  const handleSendReaction = useCallback((reactionType: string) => {
    const now = Date.now();
    if (now < reactionCooldownUntilRef.current) return;

    const cooldownMs = 1100;
    reactionCooldownUntilRef.current = now + cooldownMs;
    setReactionOnCooldown(true);
    if (reactionCooldownTimerRef.current !== null) {
      window.clearTimeout(reactionCooldownTimerRef.current);
    }
    reactionCooldownTimerRef.current = window.setTimeout(() => {
      reactionCooldownTimerRef.current = null;
      setReactionOnCooldown(false);
    }, cooldownMs);

    wsClient
      .send(MessageType.SEND_REACTION, { reactionType }, 3000)
      .catch(() => {});
  }, []);

  useEffect(() => () => {
    if (reactionCooldownTimerRef.current !== null) {
      window.clearTimeout(reactionCooldownTimerRef.current);
    }
  }, []);

  useEffect(() => {
    reactionStore.clear();
  }, [gameState?.gameId, gameState?.currentRound, roundPhase]);

  if (!gameState) {
    if (loadTimedOut) {
      return (
        <div className="dg-page min-h-screen flex items-center justify-center select-none p-4">
          <div className="glass-panel p-8 rounded-3xl text-center space-y-4 shadow-2xl max-w-sm w-full">
            <div className="text-4xl">⚠️</div>
            <h3 className="text-slate-800 font-extrabold text-lg">
              Không thể tải trận đấu
            </h3>
            <p className="text-slate-500 text-xs font-medium">
              Ván chơi có thể đã kết thúc hoặc kết nối gặp gián đoạn.
            </p>
            <button
              onClick={() => {
                resetAllSessionState();
              }}
              className="dg-danger-button bouncy-btn w-full py-3 text-sm"
            >
              Trở về trang chủ 🏠
            </button>
          </div>
        </div>
      );
    }
    return (
      <div className="dg-page min-h-screen flex items-center justify-center select-none">
        <div className="glass-panel p-8 rounded-3xl text-center space-y-4 shadow-2xl">
          <div className="w-12 h-12 border-4 border-sky-500 border-t-transparent rounded-full animate-spin mx-auto" />
          <p className="text-slate-700 font-extrabold text-sm">
            Đang tải trạng thái trận đấu...
          </p>
        </div>
      </div>
    );
  }

  // TV5 regression fix: game-service reports "FINISHED" when the game ends
  // (legacy "GAME_OVER" kept for compatibility with older payloads).
  const isGameOver =
    gameState.status === "GAME_OVER" || gameState.status === "FINISHED";

  // Audio: Background Music during gameplay & Game Over victory fanfare
  const prevGameOverRef = useRef(false);
  useEffect(() => {
    if (isGameOver) {
      if (!prevGameOverRef.current) {
        prevGameOverRef.current = true;
        audioManager.stopBGM();
        audioManager.playSFX("gameover");
      }
    } else {
      prevGameOverRef.current = false;
      audioManager.playBGM("game");
    }
    return () => {
      audioManager.stopBGM();
    };
  }, [isGameOver]);

  return (
    <div className="dg-page dg-game-page w-screen flex flex-col p-2 sm:p-3 gap-2 sm:gap-3 overflow-hidden text-slate-800 select-none">
      {/* TV7: reconnect / recovery banners — small, non-blocking */}
      {showReconnectBanner && (
        <div className="shrink-0 px-4 py-2 rounded-xl bg-rose-50 border-2 border-rose-300 text-rose-700 text-xs font-bold flex items-center justify-between gap-2 animate-pulse shadow-sm">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-rose-400" />
            <span>
              {connStatus === "DISCONNECTED"
                ? "Mất kết nối mạng..."
                : connStatus === "FAILING_OVER"
                  ? "Đang chuyển sang máy chủ dự phòng..."
                  : "Mất kết nối — đang thử kết nối lại..."}
            </span>
          </div>
          {connStatus === "DISCONNECTED" && (
            <button
              onClick={() => resetAllSessionState()}
              className="text-[10px] bg-white hover:bg-rose-100 text-rose-700 border border-rose-300 font-extrabold px-2.5 py-1 rounded-lg transition-all"
            >
              Về trang chủ
            </button>
          )}
        </div>
      )}
      {showRecoveryBanner && (
        <div className="shrink-0 px-4 py-2 rounded-xl bg-amber-50 border-2 border-amber-300 text-amber-700 text-xs font-bold flex items-center gap-2 animate-pulse shadow-sm">
          <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
          Đang đồng bộ lại ván chơi...
        </div>
      )}
      {/* Top Bar Header */}
      <div className="flex items-center gap-2 sm:gap-3 shrink-0">
        <div className="flex-1 min-w-0">
          <GameHeader
            gameState={gameState}
            isDrawer={isDrawer}
            roomId={roomId}
          />
        </div>
        {/* Desktop actions */}
        <div className="hidden md:flex items-center gap-2">
          <SoundToggle />
          <button
            onClick={() => setShowMobileScoreboard(true)}
            className="dg-icon-btn btn-3d xl:hidden"
            title="Xem bảng xếp hạng"
          >
            🏆
          </button>
          <button className="dg-icon-btn btn-3d" title="Trợ giúp">
            <span className="material-symbols-outlined text-lg">help</span>
          </button>
          <button
            onClick={() => {
              if (window.confirm("Bạn có chắc muốn rời phòng về trang chủ?")) {
                wsClient
                  .send("LEAVE_ROOM", {
                    roomId,
                    playerId,
                    username: playerStore.getState().username || "Người chơi",
                  })
                  .catch(() => {});
                resetAllSessionState();
              }
            }}
            title="Rời phòng (về trang chủ)"
            className="dg-icon-btn btn-3d !bg-rose-100 !text-rose-600"
          >
            <span className="material-symbols-outlined text-lg">logout</span>
          </button>
          <ConnectionStatus />
        </div>

        {/* Mobile actions */}
        <div className="flex md:hidden items-center gap-1.5 shrink-0">
          <SoundToggle size="sm" />
          <button
            onClick={() => setShowMobileScoreboard(true)}
            className="dg-icon-btn btn-3d !min-w-9 !min-h-9 text-sm"
            title="Xem bảng xếp hạng"
          >
            🏆
          </button>
          <button
            onClick={() => {
              if (window.confirm("Bạn có chắc muốn rời phòng về trang chủ?")) {
                wsClient
                  .send("LEAVE_ROOM", {
                    roomId,
                    playerId,
                    username: playerStore.getState().username || "Người chơi",
                  })
                  .catch(() => {});
                resetAllSessionState();
              }
            }}
            title="Rời phòng (về trang chủ)"
            className="dg-icon-btn btn-3d !min-w-9 !min-h-9 !bg-rose-100 !text-rose-600"
          >
            <span className="material-symbols-outlined text-base leading-none">
              logout
            </span>
          </button>
        </div>
      </div>

      {/* Main Game Arena Workspace */}
      <main className="dg-game-arena flex-1 relative">
        {/* Left Column 1: Leaderboard (Bảng Xếp Hạng) - hidden on mobile, visible on md+ */}
        <div className="dg-game-score-column h-full min-h-0">
          <Scoreboard
            scores={gameState.scores}
            currentPlayerId={playerId}
            currentDrawerId={gameState.drawerId}
          />
        </div>

        {/* Mobile Scoreboard Modal Overlay */}
        {showMobileScoreboard && (
          <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="glass-panel-dark max-w-sm w-full rounded-3xl p-4 max-h-[80vh] flex flex-col gap-3 shadow-2xl">
              <div className="flex justify-between items-center pb-2 border-b-2 border-sky-100">
                <h3 className="font-black text-sm text-slate-800 flex items-center gap-1.5">
                  <span>🏆</span> BẢNG XẾP HẠNG
                </h3>
                <button
                  onClick={() => setShowMobileScoreboard(false)}
                  className="text-slate-500 hover:text-slate-800 font-black text-sm p-1 rounded-lg"
                >
                  ✕
                </button>
              </div>
              <div className="flex-1 min-h-0 overflow-y-auto">
                <Scoreboard
                  scores={gameState.scores}
                  currentPlayerId={playerId}
                  currentDrawerId={gameState.drawerId}
                />
              </div>
            </div>
          </div>
        )}

        {/* Gartic-style center stage: tools beside a large drawing board */}
        <div className="dg-game-canvas-column">
          {canDraw && (
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

          <div className="flex-1 min-w-0 min-h-0 relative">
            <DrawingCanvas
              ref={canvasHandleRef}
              isDrawer={canDraw}
              color={brushColor}
              size={brushSize}
              activeTool={activeTool}
              onDrawBatch={handleDrawBatch}
              onClearCanvas={handleClearCanvas}
              externalPoints={isDrawer ? undefined : drawPoints}
              hideInternalToolbar={true}
            />
            <RoundPhaseOverlay
              gameState={gameState}
              isDrawer={isDrawer}
              onSelectWord={handleSelectWord}
            />
            {canGuess && !isGameOver && (
              <ReactionBar
                onSend={handleSendReaction}
                disabled={reactionOnCooldown}
              />
            )}
            {reactions
              .filter(
                (reaction) =>
                  reaction.gameId === gameState.gameId &&
                  reaction.roundNumber === gameState.currentRound,
              )
              .map((reaction) => (
                <div
                  key={reaction.id}
                  className="pointer-events-none absolute z-20 motion-safe:animate-bounce"
                  style={{ left: `${reaction.x}%`, top: `${reaction.y}%` }}
                >
                  <span className="text-4xl drop-shadow-lg">
                    {reaction.reactionType}
                  </span>
                  <span className="mt-1 block rounded-full border border-sky-200 bg-white/90 px-2 py-0.5 text-center text-[10px] font-bold text-slate-800 shadow-sm">
                    {reaction.displayName}
                  </span>
                </div>
              ))}
            <NetworkInspector />
          </div>
        </div>

        {/* Guessing and room chat stay visible beside the drawing board. */}
        <aside className="dg-game-social-column">
          <div className="h-full min-h-0">
            <GuessInput
              roomId={roomId}
              disabled={isDrawer || isGameOver || !canGuess}
              hasGuessed={hasGuessed}
            />
          </div>
          <div className="h-full min-h-0">
            <ChatPanel roomId={roomId} />
          </div>
        </aside>
      </main>
    </div>
  );
};
