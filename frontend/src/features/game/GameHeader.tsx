import React from 'react';
import { GameState } from '../../types/game';
import { RoundTimer } from '../../components/RoundTimer';
import { SecretWord } from './SecretWord';
import { WordHint } from './WordHint';

interface GameHeaderProps {
  gameState: GameState;
  isDrawer: boolean;
}

export const GameHeader: React.FC<GameHeaderProps> = ({ gameState, isDrawer }) => {
  return (
    <div className="bg-slate-900/80 border border-slate-800 p-4 rounded-2xl shadow-xl flex flex-wrap items-center justify-between gap-4">
      <div className="flex items-center gap-3">
        <div className="bg-slate-800 px-3 py-1.5 rounded-xl border border-slate-700">
          <span className="text-xs text-slate-400 font-medium uppercase tracking-wider block">Round</span>
          <span className="text-lg font-bold text-slate-100">
            {gameState.currentRound} <span className="text-slate-500 font-normal">/ {gameState.totalRounds}</span>
          </span>
        </div>
      </div>

      <div className="flex-1 flex justify-center">
        {isDrawer ? (
          <SecretWord secretWord={gameState.secretWord || '???'} />
        ) : (
          <WordHint hint={gameState.hint} />
        )}
      </div>

      <RoundTimer roundEndsAt={gameState.roundEndsAt} />
    </div>
  );
};
