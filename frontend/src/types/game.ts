export type GameStatus = 'WAITING' | 'STARTING' | 'IN_ROUND' | 'ROUND_ENDED' | 'GAME_OVER';

export interface PlayerScore {
  playerId: string;
  username: string;
  score: number;
  hasGuessed: boolean;
}

export interface GameState {
  roomId: string;
  status: GameStatus;
  currentRound: number;
  totalRounds: number;
  drawerId: string;
  roundStartedAt: number;
  roundEndsAt: number;
  hint: string;
  secretWord?: string;
  scores: PlayerScore[];
}

export interface GuessResult {
  roomId: string;
  playerId: string;
  status: 'CORRECT' | 'WRONG' | 'INVALID';
  scoreAwarded: number;
}

export interface DrawPoint {
  x: number;
  y: number;
  color?: string;
  size?: number;
  isNewPath?: boolean;
}
