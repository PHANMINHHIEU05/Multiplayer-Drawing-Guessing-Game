import { WSResponse } from './messageTypes';
import { MessageType } from './protocol';
import { roomStore } from '../store/roomStore';
import { gameStore } from '../store/gameStore';
import { chatStore } from '../store/chatStore';
import { guessStore } from '../store/guessStore';
import { connectionStore } from '../store/connectionStore';
import { Room, Player } from '../types/room';
import { GameState, DrawPoint } from '../types/game';
import { ChatMessage } from '../types/chat';

import { metricsStore } from '../store/metricsStore';

export function setupMessageHandlers(onResponse?: (response: WSResponse) => void): (response: WSResponse) => void {
  return (response: WSResponse) => {
    switch (response.type) {
      case MessageType.ROOM_CREATED:
      case MessageType.ROOM_JOINED:
      case MessageType.ROOM_INFO: {
        const players: Player[] = (response.players || []).map((p: any) => ({
          playerId: p.playerId,
          username: p.username,
          isHost: p.playerId === response.hostPlayerId,
        }));

        const room: Room = {
          roomId: response.roomId || '',
          name: response.name || `Room ${response.roomId || ''}`,
          status: response.status || 'LOBBY',
          hostPlayerId: response.hostPlayerId || '',
          players,
          maxPlayers: response.maxPlayers || 4,
          roundCount: response.roundCount || 5,
          roundDuration: response.roundDuration || 60,
          playerCount: response.playerCount || players.length,
        };

        roomStore.setRoom(room);
        break;
      }

      case MessageType.PLAYER_JOINED: {
        const currentRoom = roomStore.getState().room;
        if (currentRoom && response.playerId) {
          const exists = currentRoom.players.some((p) => p.playerId === response.playerId);
          if (!exists) {
            const updatedPlayers = [
              ...currentRoom.players,
              {
                playerId: response.playerId,
                username: response.username || 'Player',
                isHost: response.playerId === currentRoom.hostPlayerId,
              },
            ];
            roomStore.updatePlayers(updatedPlayers);
          }
        }
        chatStore.addMessage({
          roomId: response.roomId || '',
          playerId: 'system',
          username: 'System',
          content: `${response.username || 'A player'} has joined the room.`,
          type: 'SYSTEM',
          createdAt: Date.now(),
        });
        break;
      }

      case MessageType.PLAYER_LEFT: {
        const currentRoom = roomStore.getState().room;
        if (currentRoom && response.playerId) {
          const updatedPlayers = currentRoom.players.filter((p) => p.playerId !== response.playerId);
          roomStore.updatePlayers(updatedPlayers);
        }
        chatStore.addMessage({
          roomId: response.roomId || '',
          playerId: 'system',
          username: 'System',
          content: `${response.username || 'A player'} has left the room.`,
          type: 'SYSTEM',
          createdAt: Date.now(),
        });
        break;
      }

      case MessageType.ROOM_LEFT: {
        roomStore.clearRoom();
        gameStore.clearGame();
        chatStore.clearMessages();
        guessStore.clearGuesses();
        metricsStore.resetStrokeSequence();
        break;
      }

      case MessageType.GAME_STARTED:
      case MessageType.GAME_STATE: {
        const gameState: GameState = {
          roomId: response.roomId || '',
          status: response.status || 'IN_ROUND',
          currentRound: response.currentRound || 1,
          totalRounds: response.totalRounds || 5,
          drawerId: response.drawerId || '',
          roundStartedAt: response.roundStartedAt || Date.now(),
          roundEndsAt: response.roundEndsAt || Date.now() + 60000,
          hint: response.hint || '',
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
            roomStore.setRoom({ ...room, status: 'IN_GAME' });
          }
        }
        break;
      }

      case MessageType.PLAYER_GUESSED_CORRECTLY: {
        guessStore.addGuess({
          id: `guess_${Date.now()}_${Math.random()}`,
          roomId: response.roomId || '',
          playerId: response.playerId || '',
          username: response.username || response.playerId || 'Người chơi',
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
          type: payload.type || 'USER',
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
          type: m.type || 'USER',
          createdAt: m.createdAt || Date.now(),
        }));
        chatStore.setMessages(formatted);
        break;
      }

      // ─── Drawing Events ─────────────────────────────────────────────
      case MessageType.DRAW_EVENT: {
        const payload = response.payload || response;
        const pointData = payload.point || payload;
        const color = pointData.color || payload.color || '#000000';
        const isEraser = pointData.tool === 'ERASER' || payload.tool === 'ERASER' || color.toUpperCase() === '#FFFFFF';
        const point: DrawPoint = {
          x: pointData.x ?? 0,
          y: pointData.y ?? 0,
          color,
          size: pointData.size ?? payload.size ?? 4,
          isNewPath: pointData.isNewPath ?? payload.isNewPath ?? false,
          tool: isEraser ? 'ERASER' : 'BRUSH',
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
        const batchColor = payload.color || '#000000';
        const batchIsEraser = payload.tool === 'ERASER' || batchColor.toUpperCase() === '#FFFFFF';
        const points: DrawPoint[] = rawPoints.map((p: any) => {
          const ptColor = p.color || batchColor;
          const isPtEraser = p.tool === 'ERASER' || batchIsEraser || ptColor.toUpperCase() === '#FFFFFF';
          return {
            x: p.x ?? 0,
            y: p.y ?? 0,
            color: ptColor,
            size: p.size ?? payload.size ?? 4,
            isNewPath: p.isNewPath ?? false,
            tool: isPtEraser ? 'ERASER' : 'BRUSH',
            strokeId: p.strokeId || payload.strokeId,
            timestamp: p.timestamp,
          };
        });
        gameStore.addDrawPoints(points);
        metricsStore.recordDrawBatchReceived(points.length, payload.strokeId, payload.seqStart);
        break;
      }

      case MessageType.CANVAS_CLEARED: {
        gameStore.clearDrawPoints();
        metricsStore.resetStrokeSequence();
        break;
      }

      case MessageType.SYNC_CANVAS_STATE: {
        const payload = response.payload || response;
        const points: DrawPoint[] = (payload.points || []).map((p: any) => ({
          x: p.x ?? 0,
          y: p.y ?? 0,
          color: p.color ?? '#f8fafc',
          size: p.size ?? 4,
          isNewPath: p.isNewPath ?? false,
          timestamp: p.timestamp,
        }));
        gameStore.setDrawPoints(points);
        break;
      }

      case MessageType.ROUND_STARTED: {
        // Clear canvas and guess stream when a new round starts
        gameStore.clearDrawPoints();
        guessStore.clearGuesses();
        metricsStore.resetStrokeSequence();
        break;
      }

      case MessageType.ERROR: {
        const errMsg = response.message || response.error?.message || 'Error from server';
        connectionStore.setLastError(errMsg);
        break;
      }
    }

    if (onResponse) {
      onResponse(response);
    }
  };
}
