import React from 'react';
import { GameState } from '../../types/game';
import { RoundTimer } from '../../components/RoundTimer';
import { SecretWord } from './SecretWord';
import { WordHint } from './WordHint';

interface GameHeaderProps {
  gameState: GameState;
  isDrawer: boolean;
  roomId?: string;
}

export const GameHeader: React.FC<GameHeaderProps> = ({ gameState, isDrawer, roomId }) => {
  return (
    <div className="glass-panel-game px-4 py-2 flex items-center justify-between gap-3 shrink-0 shadow-lg">
      {/* Left: Logo & Room info */}
      <div className="flex items-center gap-2">
        <span className="text-2xl">🎨</span>
        <div className="hidden sm:block">
          <span className="bubbly-logo text-base font-black leading-none block">Dopamine</span>
          {roomId && <span className="text-[9px] font-black text-sky-200 uppercase tracking-widest">#{roomId}</span>}
        </div>
      </div>

      {/* Center: Round & Secret Word / Word Hint */}
      <div className="flex items-center gap-2 sm:gap-4 flex-1 justify-center max-w-lg">
        {/* Round Badge */}
        <div className="bg-indigo-900/80 border border-indigo-400/40 text-white font-extrabold text-[11px] sm:text-xs px-3 py-1 rounded-full shadow-md whitespace-nowrap">
          ROUND {gameState.currentRound}/{gameState.totalRounds}
        </div>

        {/* Word or Hint */}
        {isDrawer ? (
          <SecretWord secretWord={gameState.secretWord || '???'} />
        ) : (
          <WordHint hint={gameState.hint} />
        )}
      </div>

      {/* Right: Round Timer */}
      <div className="flex items-center gap-2">
        <RoundTimer roundEndsAt={gameState.roundEndsAt} />
      </div>
    </div>
  );
};

