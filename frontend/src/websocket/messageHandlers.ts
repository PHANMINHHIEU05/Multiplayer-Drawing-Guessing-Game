import { WSResponse } from "./messageTypes";
import { MessageType } from "./protocol";
import { wsClient } from "./WebSocketClient";
import { roomStore } from "../store/roomStore";
import { gameStore } from "../store/gameStore";
import { chatStore } from "../store/chatStore";
import { guessStore } from "../store/guessStore";
import { connectionStore } from "../store/connectionStore";
import { playerStore } from "../store/playerStore";
import { Room, Player } from "../types/room";
import { GameState, DrawPoint } from "../types/game";
import { ChatMessage } from "../types/chat";

import { metricsStore } from "../store/metricsStore";
import { noticeStore } from "../store/noticeStore";
import { reactionStore } from "../store/reactionStore";
import { translateError } from "../utils/errorTranslation";

export function setupMessageHandlers(
  onResponse?: (response: WSResponse) => void,
): (response: WSResponse) => void {
  return (response: WSResponse) => {
    switch (response.type) {
      case MessageType.ROOM_CREATED:
      case MessageType.ROOM_JOINED:
      case MessageType.ROOM_CATEGORIES_UPDATED:
      case MessageType.ROOM_INFO: {
        // TV8: the Gateway issues a signed game-session credential on CREATE/JOIN —
        // persist it; it is the only accepted resume proof from now on.
        if (
          response.sessionToken &&
          (response.type === MessageType.ROOM_CREATED ||
            response.type === MessageType.ROOM_JOINED)
        ) {
          playerStore.setSessionToken(response.sessionToken);
        }

        const players: Player[] = (response.players || []).map((p: any) => ({
          playerId: p.playerId,
          username: p.username,
          isHost: p.playerId === response.hostPlayerId,
          // TV10: authoritative server-side readiness (host implicitly ready)
          ready: !!p.ready || p.playerId === response.hostPlayerId,
        }));

        const room: Room = {
          roomId: response.roomId || "",
          name: response.name || `Room ${response.roomId || ""}`,
          status: response.status || "LOBBY",
          hostPlayerId: response.hostPlayerId || "",
          players,
          maxPlayers: response.maxPlayers || 4,
          roundCount: response.roundCount || 5,
          roundDuration: response.roundDuration || 60,
          playerCount: response.playerCount || players.length,
          selectedCategories: Array.isArray(response.selectedCategories)
            ? response.selectedCategories
            : ["ANIMALS", "FOOD", "OBJECTS", "PLACES", "NATURE", "TECHNOLOGY"],
        };

        roomStore.setRoom(room);
        break;
      }

      case MessageType.PLAYER_JOINED: {
        const currentRoom = roomStore.getState().room;
        if (currentRoom && response.playerId) {
          const exists = currentRoom.players.some(
            (p) => p.playerId === response.playerId,
          );
          if (!exists) {
            const updatedPlayers = [
              ...currentRoom.players,
              {
                playerId: response.playerId,
                username: response.username || "Player",
                isHost: response.playerId === currentRoom.hostPlayerId,
              },
            ];
            roomStore.updatePlayers(updatedPlayers);
          }
        }
        const joinedUser = response.username || "Người chơi";
        const myPlayerId = playerStore.getState().playerId;
        if (response.playerId !== myPlayerId) {
          noticeStore.pushNotice({
            type: "INFO",
            message: `${joinedUser} đã vào phòng.`,
            durationMs: 3000,
          });
        }
        break;
      }

      case MessageType.PLAYER_LEFT: {
        const currentRoom = roomStore.getState().room;
        const myPlayerId = playerStore.getState().playerId;
        const leftPlayerId = response.playerId;
        const leftUsername = response.username || "Người chơi";

        if (response.players && Array.isArray(response.players)) {
          const updatedPlayers: Player[] = response.players.map((p: any) => ({
            playerId: p.playerId,
            username: p.username,
            isHost: p.playerId === response.hostPlayerId,
            ready: !!p.ready || p.playerId === response.hostPlayerId,
          }));
          roomStore.updatePlayers(updatedPlayers);
          if (response.hostPlayerId && currentRoom) {
            roomStore.setRoom({
              ...currentRoom,
              hostPlayerId: response.hostPlayerId,
              players: updatedPlayers,
              playerCount: updatedPlayers.length,
            });
          }
        } else if (currentRoom && leftPlayerId) {
          const newHostId = response.hostPlayerId || currentRoom.hostPlayerId;
          const updatedPlayers = currentRoom.players
            .filter((p) => p.playerId !== leftPlayerId)
            .map((p) => ({
              ...p,
              isHost: p.playerId === newHostId,
              ready: !!p.ready || p.playerId === newHostId,
            }));
          roomStore.setRoom({
            ...currentRoom,
            hostPlayerId: newHostId,
            players: updatedPlayers,
            playerCount: updatedPlayers.length,
          });
        }

        // Update gameState scores so scoreboard removes leaving player
        const currentGameState = gameStore.getState().gameState;
        if (currentGameState && leftPlayerId) {
          gameStore.setGameState({
            ...currentGameState,
            scores: (currentGameState.scores || []).filter(
              (s) => s.playerId !== leftPlayerId,
            ),
          });
        }

        // Host migration notice
        if (
          response.hostPlayerId &&
          response.hostPlayerId === myPlayerId &&
          currentRoom &&
          currentRoom.hostPlayerId !== myPlayerId
        ) {
          noticeStore.pushNotice({
            type: "INFO",
            message: "Bạn đã trở thành chủ phòng.",
            durationMs: 4000,
          });
        }

        // Notification toast (no fake chat message)
        noticeStore.pushNotice({
          type: "INFO",
          message: `${leftUsername} đã rời phòng.`,
          durationMs: 4000,
        });
        break;
      }

      case MessageType.ROOM_LEFT: {
        // TV8: explicit leave — clear the signed credential + resume metadata so an
        // old token can never silently restore the player into this room.
        playerStore.clearSessionToken();
        roomStore.clearRoom();
        gameStore.clearGame();
        chatStore.clearMessages();
        guessStore.clearGuesses();
        metricsStore.resetStrokeSequence();
        break;
      }

      // TV10: lobby readiness update (cross-Gateway control event)
      case MessageType.PLAYER_READY_CHANGED: {
        const players: Player[] = (response.players || []).map((p: any) => ({
          playerId: p.playerId,
          username: p.username,
          isHost: p.playerId === response.hostPlayerId,
          ready: !!p.ready || p.playerId === response.hostPlayerId,
        }));
        if (players.length > 0) {
          roomStore.updatePlayers(players);
        }
        break;
      }

      // TV10 REMATCH: room reset to WAITING — everyone returns to the Lobby
      case MessageType.ROOM_RESET: {
        const players: Player[] = (response.players || []).map((p: any) => ({
          playerId: p.playerId,
          username: p.username,
          isHost: p.playerId === response.hostPlayerId,
          ready: !!p.ready || p.playerId === response.hostPlayerId,
        }));
        const currentRoom = roomStore.getState().room;
        roomStore.setRoom({
          roomId: response.roomId || currentRoom?.roomId || "",
          name: response.name || currentRoom?.name || "",
          status: "WAITING",
          hostPlayerId:
            response.hostPlayerId || currentRoom?.hostPlayerId || "",
          players,
          maxPlayers: response.maxPlayers || currentRoom?.maxPlayers || 4,
          roundCount: response.roundCount || currentRoom?.roundCount || 5,
          roundDuration:
            response.roundDuration || currentRoom?.roundDuration || 60,
          playerCount: response.playerCount || players.length,
          selectedCategories: Array.isArray(response.selectedCategories)
            ? response.selectedCategories
            : currentRoom?.selectedCategories || [
                "ANIMALS",
                "FOOD",
                "OBJECTS",
                "PLACES",
                "NATURE",
                "TECHNOLOGY",
              ],
        });
        // reset ALL match-specific frontend state
        gameStore.clearGame();
        guessStore.clearGuesses();
        chatStore.clearMessages();
        metricsStore.resetStrokeSequence();
        break;
      }

      // TV10 KICK: the target exits to Home; others update their player list
      case MessageType.PLAYER_KICKED: {
        const myId = playerStore.getState().playerId;
        if (response.targetPlayerId === myId) {
          // You were kicked — clear everything and surface a clear message
          playerStore.clearSessionToken();
          roomStore.clearRoom();
          gameStore.clearGame();
          chatStore.clearMessages();
          guessStore.clearGuesses();
          connectionStore.setLastError("Bạn đã bị chủ phòng mời khỏi phòng.");
          break;
        }
        const currentRoom = roomStore.getState().room;
        if (currentRoom) {
          const updated = (
            response.players ||
            currentRoom.players.filter(
              (p: any) => p.playerId !== response.targetPlayerId,
            )
          ).map((p: any) => ({
            playerId: p.playerId,
            username: p.username,
            isHost: p.playerId === currentRoom.hostPlayerId,
            ready: !!p.ready || p.playerId === currentRoom.hostPlayerId,
          }));
          roomStore.updatePlayers(updated);
        }
        break;
      }

      case MessageType.GAME_STARTED:
      case MessageType.GAME_STATE: {
        const previous = gameStore.getState().gameState;
        const phase = response.roundPhase || "DRAWING";
        const phaseOrder: Record<string, number> = {
          WORD_SELECTION: 0,
          COUNTDOWN: 1,
          DRAWING: 2,
          ROUND_RECAP: 3,
        };
        const sameMatch =
          previous && response.gameId && previous.gameId === response.gameId;
        if (
          sameMatch &&
          (Number(response.currentRound || 0) < previous.currentRound ||
            (Number(response.currentRound || 0) === previous.currentRound &&
              (phaseOrder[phase] ?? 0) <
                (phaseOrder[previous.roundPhase || "DRAWING"] ?? 0)))
        )
          break;
        if (
          previous &&
          response.gameId &&
          previous.gameId &&
          previous.gameId !== response.gameId &&
          response.type !== MessageType.GAME_STARTED
        )
          break;
        const gameState: GameState = {
          roomId: response.roomId || "",
          status: response.status || "IN_ROUND",
          currentRound: response.currentRound || 1,
          totalRounds: response.totalRounds || 5,
          drawerId: response.drawerId || "",
          roundStartedAt: response.roundStartedAt || Date.now(),
          roundEndsAt: response.roundEndsAt || Date.now() + 60000,
          gameId: response.gameId,
          roundPhase: phase,
          phaseStartedAt: response.phaseStartedAt || 0,
          phaseEndsAt: response.phaseEndsAt || 0,
          roundDurationSeconds: response.roundDurationSeconds || 60,
          wordChoices: (response.wordChoices || []).map((choice: any) => ({
            choiceId: choice.choiceId,
            displayWord: choice.displayWord,
          })),
          roundRecap: response.roundRecap
            ? {
                ...response.roundRecap,
                correctPlayerIds: response.roundRecap.correctPlayerIds || [],
                scoreDeltas: (response.roundRecap.scoreDeltas || []).map(
                  (delta: any) => ({
                    playerId: delta.playerId,
                    username: delta.username,
                    roundDelta: Number(delta.roundDelta || 0),
                    totalScore: Number(delta.totalScore || 0),
                  }),
                ),
              }
            : undefined,
          awards: response.awards || [],
          hint: response.hint || "",
          secretWord: response.secretWord,
          scores: (response.scores || []).map((s: any) => ({
            playerId: s.playerId,
            username: s.username,
            score: s.score || 0,
            hasGuessed: !!s.hasGuessed,
          })),
        };

        gameStore.setGameState(gameState);
        if (response.type === MessageType.GAME_STARTED) {
          const room = roomStore.getState().room;
          if (room) {
            roomStore.setRoom({ ...room, status: "IN_GAME" });
          }
          const myPlayerId = playerStore.getState().playerId;
          // GAME_STARTED is intentionally public and strips private choices. Fetch the
          // viewer-filtered state immediately so the drawer has the full 10s selection.
          const activeRoomId = response.roomId || gameState.roomId;
          if (activeRoomId) {
            wsClient
              .send(
                MessageType.GET_GAME_STATE,
                { roomId: activeRoomId, playerId: myPlayerId },
                5000,
              )
              .catch(() => {});
          }
          if (
            gameState.roundPhase === "WORD_SELECTION" &&
            gameState.drawerId !== myPlayerId
          ) {
            noticeStore.pushNotice({
              id: "game_started_guesser",
              type: "INFO",
              message: "Người vẽ đang chọn từ…",
              durationMs: 3000,
            });
          } else if (
            gameState.drawerId === myPlayerId &&
            gameState.secretWord
          ) {
            noticeStore.pushNotice({
              id: "game_started_drawer",
              type: "SUCCESS",
              message: `Đến lượt bạn vẽ! Từ khóa: ${gameState.secretWord}`,
              durationMs: 6000,
            });
          } else if (
            gameState.drawerId &&
            gameState.drawerId !== myPlayerId &&
            gameState.roundPhase === "DRAWING"
          ) {
            const drawerPlayer = room?.players.find(
              (p) => p.playerId === gameState.drawerId,
            );
            const drawerName = drawerPlayer?.username || "Người chơi";
            noticeStore.pushNotice({
              id: "game_started_guesser",
              type: "INFO",
              message: `${drawerName} đang vẽ... Hãy chuẩn bị đoán!`,
              durationMs: 4000,
            });
          }
        }
        break;
      }

      case MessageType.WORD_SELECTION_STARTED:
      case MessageType.ROUND_COUNTDOWN_STARTED:
      case MessageType.ROUND_STARTED:
      case MessageType.ROUND_RECAP_STARTED: {
        const payload = response.payload || response;
        const current = gameStore.getState().gameState;
        if (
          current &&
          ((payload.gameId &&
            current.gameId &&
            payload.gameId !== current.gameId) ||
            (payload.currentRound &&
              Number(payload.currentRound) < current.currentRound))
        )
          break;
        if (
          response.type === MessageType.WORD_SELECTION_STARTED ||
          response.type === MessageType.ROUND_STARTED
        ) {
          gameStore.clearDrawPoints();
          guessStore.clearGuesses();
          metricsStore.resetStrokeSequence();
          reactionStore.clear();
        }
        const rid = roomStore.getState().room?.roomId || current?.roomId;
        const pid = playerStore.getState().playerId;
        if (rid)
          wsClient
            .send(
              MessageType.GET_GAME_STATE,
              { roomId: rid, playerId: pid },
              5000,
            )
            .catch(() => {});
        break;
      }

      case MessageType.HINT_UPDATED: {
        const payload = response.payload || response;
        const current = gameStore.getState().gameState;
        if (
          current &&
          current.roundPhase === "DRAWING" &&
          (!payload.gameId ||
            !current.gameId ||
            payload.gameId === current.gameId) &&
          (!payload.currentRound ||
            Number(payload.currentRound) === current.currentRound)
        ) {
          gameStore.setGameState({
            ...current,
            hint: String(payload.hint || current.hint),
          });
        }
        break;
      }

      case MessageType.REACTION: {
        const payload = response.payload || response;
        const current = gameStore.getState().gameState;
        if (
          current &&
          current.roundPhase === "DRAWING" &&
          (!payload.gameId || payload.gameId === current.gameId) &&
          Number(payload.roundNumber || payload.currentRound) ===
            current.currentRound
        ) {
          reactionStore.add({
            playerId: payload.playerId || "",
            displayName:
              payload.displayName || payload.username || "Người chơi",
            reactionType: payload.reactionType || "",
            gameId: payload.gameId || current.gameId || "",
            roundNumber: Number(payload.roundNumber || payload.currentRound),
          });
        }
        break;
      }

      case MessageType.PLAYER_GUESSED_CORRECTLY: {
        guessStore.addGuess({
          id: `guess_${Date.now()}_${Math.random()}`,
          roomId: response.roomId || "",
          playerId: response.playerId || "",
          username: response.username || response.playerId || "Người chơi",
          guess: `đã đoán đúng từ khóa! (+${response.scoreAwarded || 0} điểm)`,
          isCorrect: true,
          timestamp: Date.now(),
        });
        break;
      }

      case MessageType.CHAT_MESSAGE: {
        const payload = response.payload || response;
        const msg: ChatMessage = {
          messageId: payload.messageId,
          roomId: payload.roomId,
          playerId: payload.playerId,
          username: payload.username,
          content: payload.content,
          type: payload.type || "USER",
          createdAt: payload.createdAt || Date.now(),
        };
        chatStore.addMessage(msg);
        break;
      }

      case MessageType.CHAT_HISTORY: {
        const rawMessages = response.messages || [];
        const formatted: ChatMessage[] = rawMessages.map((m: any) => ({
          messageId: m.messageId,
          roomId: m.roomId,
          playerId: m.playerId,
          username: m.username,
          content: m.content,
          type: m.type || "USER",
          createdAt: m.createdAt || Date.now(),
        }));
        chatStore.setMessages(formatted);
        break;
      }

      // ─── Drawing Events ─────────────────────────────────────────────
      case MessageType.DRAW_EVENT: {
        const payload = response.payload || response;
        const pointData = payload.point || payload;
        const color = pointData.color || payload.color || "#000000";
        const isEraser =
          pointData.tool === "ERASER" ||
          payload.tool === "ERASER" ||
          color.toUpperCase() === "#FFFFFF";
        const point: DrawPoint = {
          x: pointData.x ?? 0,
          y: pointData.y ?? 0,
          color,
          size: pointData.size ?? payload.size ?? 4,
          isNewPath: pointData.isNewPath ?? payload.isNewPath ?? false,
          tool: isEraser ? "ERASER" : "BRUSH",
          strokeId: payload.strokeId || pointData.strokeId,
          timestamp: pointData.timestamp ?? payload.timestamp,
        };
        gameStore.addDrawPoint(point);
        metricsStore.recordDrawBatchReceived(1, payload.strokeId, payload.seq);
        break;
      }

      case MessageType.DRAW_BATCH:
      case MessageType.DRAW_BATCH_EVENT: {
        const payload = response.payload || response;
        const rawPoints = payload.points || [];
        const batchColor = payload.color || "#000000";
        const batchIsEraser =
          payload.tool === "ERASER" || batchColor.toUpperCase() === "#FFFFFF";
        const points: DrawPoint[] = rawPoints.map((p: any) => {
          const ptColor = p.color || batchColor;
          const isPtEraser =
            p.tool === "ERASER" ||
            batchIsEraser ||
            ptColor.toUpperCase() === "#FFFFFF";
          return {
            x: p.x ?? 0,
            y: p.y ?? 0,
            color: ptColor,
            size: p.size ?? payload.size ?? 4,
            isNewPath: p.isNewPath ?? false,
            tool: isPtEraser ? "ERASER" : "BRUSH",
            strokeId: p.strokeId || payload.strokeId,
            timestamp: p.timestamp,
          };
        });
        gameStore.addDrawPoints(points);
        metricsStore.recordDrawBatchReceived(
          points.length,
          payload.strokeId,
          payload.seqStart,
        );
        break;
      }

      case MessageType.CANVAS_CLEARED: {
        gameStore.clearDrawPoints();
        metricsStore.resetStrokeSequence();
        break;
      }

      case MessageType.SYNC_CANVAS_STATE: {
        // TV7: event-replay recovery response. Server sends semantic events
        // (DRAW_START / DRAW_BATCH / DRAW_END / CLEAR_CANVAS) with streamId ordering.
        const payload = response.payload || response;
        const events: any[] = payload.events || [];
        const recovered: DrawPoint[] = [];
        // strokeId -> style from DRAW_START (color/width live in the START frame)
        const strokeStyles = new Map<string, { color: string; size: number }>();

        for (const ev of events) {
          switch (ev.type) {
            case "DRAW_START": {
              const colorHex =
                "#" +
                [ev.r, ev.g, ev.b]
                  .map((c: number) =>
                    Math.max(0, Math.min(255, c)).toString(16).padStart(2, "0"),
                  )
                  .join("");
              const isEraser = ev.r === 255 && ev.g === 255 && ev.b === 255;
              const style = { color: colorHex, size: ev.width ?? 4 };
              strokeStyles.set(ev.strokeId, style);
              recovered.push({
                x: Number(ev.x),
                y: Number(ev.y),
                color: colorHex,
                size: ev.width ?? 4,
                tool: isEraser ? "ERASER" : "BRUSH",
                strokeId: ev.strokeId,
                isNewPath: true,
              });
              break;
            }
            case "DRAW_BATCH": {
              const style = strokeStyles.get(ev.strokeId) ?? {
                color: "#000000",
                size: 4,
              };
              const pts = String(ev.points || "")
                .split(" ")
                .filter(Boolean);
              for (const p of pts) {
                const [x, y] = p.split(",").map(Number);
                recovered.push({
                  x,
                  y,
                  color: style.color,
                  size: style.size,
                  tool:
                    style.color.toUpperCase() === "#FFFFFF"
                      ? "ERASER"
                      : "BRUSH",
                  strokeId: ev.strokeId,
                  isNewPath: false,
                });
              }
              break;
            }
            case "DRAW_END":
              // nothing to render
              break;
            case "CLEAR_CANVAS":
              // Replay boundary: everything before the clear is compacted away server-side,
              // but be defensive — clear anything accumulated before this marker.
              recovered.length = 0;
              strokeStyles.clear();
              break;
          }
        }

        gameStore.setDrawPoints(recovered);
        break;
      }

      case MessageType.ROUND_ENDED: {
        const payload = response.payload || response;
        const revealed = payload.revealedWord || payload.word || "";
        const currentGameState = gameStore.getState().gameState;
        if (currentGameState) {
          gameStore.setGameState({
            ...currentGameState,
            status: "ROUND_ENDED",
            secretWord: revealed || currentGameState.secretWord,
          });
        }
        if (revealed) {
          noticeStore.pushNotice({
            id: "round_ended_revealed",
            type: "INFO",
            message: `Hết lượt! Đáp án là: ${revealed}`,
            durationMs: 5000,
          });
        } else {
          noticeStore.pushNotice({
            id: "round_ended_revealed",
            type: "INFO",
            message: `Hết lượt vẽ!`,
            durationMs: 4000,
          });
        }
        break;
      }

      case "PLAYER_RECONNECTED": {
        const myPlayerId = playerStore.getState().playerId;
        const reconnectedId = response.playerId;
        const reconnectedUser = response.username || "Một người chơi";
        const currentRoom = roomStore.getState().room;
        if (currentRoom && reconnectedId) {
          const updated = currentRoom.players.map((p) =>
            p.playerId === reconnectedId ? { ...p, connected: true } : p,
          );
          roomStore.updatePlayers(updated);
        }
        if (reconnectedId !== myPlayerId) {
          noticeStore.pushNotice({
            type: "INFO",
            message: `${reconnectedUser} đã kết nối lại.`,
            durationMs: 3000,
          });
        }
        break;
      }

      case MessageType.PLAYER_DISCONNECTED:
      case "PLAYER_DISCONNECTED": {
        const myPlayerId = playerStore.getState().playerId;
        const disconnectedId = response.playerId;
        const disconnectedUser = response.username || "Một người chơi";
        const currentRoom = roomStore.getState().room;
        if (currentRoom && disconnectedId) {
          const updated = currentRoom.players.map((p) =>
            p.playerId === disconnectedId ? { ...p, connected: false } : p,
          );
          roomStore.updatePlayers(updated);
        }
        if (disconnectedId !== myPlayerId) {
          noticeStore.pushNotice({
            type: "WARNING",
            message: `${disconnectedUser} đã mất kết nối hoặc thoát.`,
            durationMs: 4000,
          });
        }
        break;
      }

      case MessageType.GAME_FINISHED:
      case MessageType.GAME_ENDED:
      case "GAME_FINISHED":
      case "GAME_ENDED": {
        const current = gameStore.getState().gameState;
        const rawScores = response.scores || response.payload?.scores || [];
        const formattedScores = rawScores.map((s: any) => ({
          playerId: s.playerId,
          username: s.username || s.playerId,
          score: s.score ?? s.finalScore ?? 0,
        }));
        if (current) {
          gameStore.setGameState({
            ...current,
            status: "FINISHED",
            roundPhase: "",
            gameId: response.gameId || current.gameId,
            awards:
              response.awards ||
              response.payload?.awards ||
              current.awards ||
              [],
            scores:
              formattedScores.length > 0 ? formattedScores : current.scores,
          });
        } else {
          gameStore.setGameState({
            roomId: response.roomId || "",
            gameId: response.gameId || response.payload?.gameId,
            status: "FINISHED",
            currentRound: response.currentRound || 5,
            totalRounds: response.totalRounds || 5,
            drawerId: "",
            roundStartedAt: 0,
            roundEndsAt: 0,
            hint: "",
            roundPhase: "",
            awards: response.awards || response.payload?.awards || [],
            scores: formattedScores,
          });
        }
        noticeStore.pushNotice({
          type: "SUCCESS",
          message: "Trận đấu đã kết thúc!",
          durationMs: 5000,
        });
        break;
      }

      case MessageType.ERROR: {
        const rawErr =
          response.message || response.error?.message || "Error from server";
        const friendly = translateError(rawErr);
        connectionStore.setLastError(friendly);
        noticeStore.pushNotice({
          type: "ERROR",
          message: friendly,
          durationMs: 4000,
        });
        break;
      }
    }

    if (onResponse) {
      onResponse(response);
    }
  };
}
