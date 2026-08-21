export interface Player {
  playerId: string;
  username: string;
  isHost?: boolean;
}

export type RoomStatus = 'WAITING' | 'IN_GAME' | 'FINISHED' | 'LOBBY';

export interface Room {
  roomId: string;
  name?: string;
  status: RoomStatus;
  hostPlayerId: string;
  players: Player[];
  maxPlayers: number;
  roundCount: number;
  roundDuration: number;
  playerCount: number;
}
