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
  x: number;          // Normalized coordinate (0.0 - 1.0)
  y: number;          // Normalized coordinate (0.0 - 1.0)
  color: string;      // Hex color code (e.g. "#EF4444")
  size: number;       // Stroke width in pixels (2-20)
  isNewPath: boolean;  // true: PointerDown (new path), false: PointerMove (continue)
  timestamp?: number; // Unix timestamp (ms) for latency tracking
}
