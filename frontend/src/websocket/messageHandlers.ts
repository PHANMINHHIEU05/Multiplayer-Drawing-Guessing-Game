import { WSResponse } from './messageTypes';
import { MessageType } from './protocol';
import { roomStore } from '../store/roomStore';
import { gameStore } from '../store/gameStore';
import { chatStore } from '../store/chatStore';
import { connectionStore } from '../store/connectionStore';
import { Room, Player } from '../types/room';
import { GameState } from '../types/game';
import { ChatMessage } from '../types/chat';

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
          roomId: response.roomId,
          name: response.name || `Room ${response.roomId}`,
          status: response.status || 'LOBBY',
          hostPlayerId: response.hostPlayerId,
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
        break;
      }

      case MessageType.GAME_STARTED:
      case MessageType.GAME_STATE: {
        const gameState: GameState = {
          roomId: response.roomId,
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
        chatStore.addMessage({
          roomId: response.roomId || '',
          playerId: 'system',
          username: 'System',
          content: `Player ${response.playerId} guessed the word correctly! (+${response.scoreAwarded || 0} pts)`,
          type: 'SYSTEM',
          createdAt: Date.now(),
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
