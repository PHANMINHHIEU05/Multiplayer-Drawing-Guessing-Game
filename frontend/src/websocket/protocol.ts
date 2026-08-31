import { WSRequest } from './messageTypes';

export function generateRequestId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'req_' + Math.random().toString(36).substring(2, 11) + '_' + Date.now();
}

export function createWSRequest<T>(type: string, payload: T): WSRequest<T> {
  return {
    type,
    requestId: generateRequestId(),
    payload,
  };
}

export const MessageType = {
  // Outbound
  CREATE_ROOM: 'CREATE_ROOM',
  JOIN_ROOM: 'JOIN_ROOM',
  RESUME_SESSION: 'RESUME_SESSION',
  GET_ROOM: 'GET_ROOM',
  LEAVE_ROOM: 'LEAVE_ROOM',
  START_GAME: 'START_GAME',
  GET_GAME_STATE: 'GET_GAME_STATE',
  SUBMIT_GUESS: 'SUBMIT_GUESS',
  SEND_CHAT: 'SEND_CHAT',
  GET_RECENT_CHAT: 'GET_RECENT_CHAT',

  // Drawing Outbound (Client → Server)
  DRAW_POINT: 'DRAW_POINT',
  DRAW_BATCH: 'DRAW_BATCH',
  CLEAR_CANVAS: 'CLEAR_CANVAS',

  // Inbound / Broadcast
  ROOM_CREATED: 'ROOM_CREATED',
  ROOM_JOINED: 'ROOM_JOINED',
  SESSION_RESUMED: 'SESSION_RESUMED',
  ROOM_INFO: 'ROOM_INFO',
  ROOM_LEFT: 'ROOM_LEFT',
  PLAYER_JOINED: 'PLAYER_JOINED',
  PLAYER_LEFT: 'PLAYER_LEFT',
  GAME_STARTED: 'GAME_STARTED',
  GAME_STATE: 'GAME_STATE',
  ROUND_STARTED: 'ROUND_STARTED',
  ROUND_ENDED: 'ROUND_ENDED',
  GAME_ENDED: 'GAME_ENDED',
  GUESS_RESULT: 'GUESS_RESULT',
  PLAYER_GUESSED_CORRECTLY: 'PLAYER_GUESSED_CORRECTLY',
  CHAT_MESSAGE: 'CHAT_MESSAGE',
  CHAT_HISTORY: 'CHAT_HISTORY',
  ERROR: 'ERROR',

  // Drawing Inbound (Server → Client broadcast)
  DRAW_EVENT: 'DRAW_EVENT',
  DRAW_BATCH_EVENT: 'DRAW_BATCH_EVENT',
  CANVAS_CLEARED: 'CANVAS_CLEARED',
  SYNC_CANVAS_STATE: 'SYNC_CANVAS_STATE',
} as const;
