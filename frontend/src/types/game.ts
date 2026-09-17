export type GameStatus = 'WAITING' | 'STARTING' | 'IN_ROUND' | 'PLAYING' | 'ROUND_ENDED' | 'GAME_OVER' | 'FINISHED';
export type RoundPhase = 'WORD_SELECTION' | 'COUNTDOWN' | 'DRAWING' | 'ROUND_RECAP' | '';

export interface WordChoice {
  choiceId: string;
  displayWord: string;
}

export interface RoundScoreDelta {
  playerId: string;
  username: string;
  roundDelta: number;
  totalScore: number;
}

export interface RoundRecap {
  roundNumber: number;
  answer: string;
  scoreDeltas: RoundScoreDelta[];
  fastestPlayerId?: string;
  fastestUsername?: string;
  fastestElapsedMillis?: number;
  correctPlayerIds?: string[];
}

export interface MatchAward {
  type: string;
  label: string;
  playerId: string;
  username: string;
  value: number;
  elapsedMillis?: number;
}

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
  gameId?: string;
  roundPhase?: RoundPhase;
  phaseStartedAt?: number;
  phaseEndsAt?: number;
  roundDurationSeconds?: number;
  wordChoices?: WordChoice[];
  roundRecap?: RoundRecap;
  awards?: MatchAward[];
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

export type DrawingTool = 'BRUSH' | 'ERASER';

export interface RemoteStrokeState {
  strokeId: string;
  tool: DrawingTool;
  color: string;
  width: number;
  round: number;
  lastX?: number;
  lastY?: number;
}

export interface DrawPoint {
  x: number;          // Normalized coordinate (0.0 - 1.0)
  y: number;          // Normalized coordinate (0.0 - 1.0)
  color: string;      // Hex color code (e.g. "#EF4444")
  size: number;       // Stroke width in pixels (2-20)
  isNewPath: boolean;  // true: PointerDown (new path), false: PointerMove (continue)
  tool?: DrawingTool; // 'BRUSH' or 'ERASER'
  strokeId?: string;  // Unique identifier for the stroke
  timestamp?: number; // Unix timestamp (ms) for latency tracking
}
