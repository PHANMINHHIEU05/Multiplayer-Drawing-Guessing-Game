import React from "react";
import { GameState } from "../../types/game";
import { RoundTimer } from "../../components/RoundTimer";
import { SecretWord } from "./SecretWord";
import { WordHint } from "./WordHint";

interface GameHeaderProps {
  gameState: GameState;
  isDrawer: boolean;
  roomId?: string;
  actions?: React.ReactNode;
}

export const GameHeader: React.FC<GameHeaderProps> = ({
  gameState,
  isDrawer,
  roomId,
  actions,
}) => {
  const phase = gameState.roundPhase || "DRAWING";
  const timerDeadline =
    phase === "DRAWING"
      ? gameState.roundEndsAt
      : gameState.phaseEndsAt || gameState.roundEndsAt;
  const timerDuration =
    phase === "WORD_SELECTION"
      ? 10
      : phase === "COUNTDOWN"
        ? 3
        : phase === "ROUND_RECAP"
          ? 3
          : gameState.roundDurationSeconds || 60;

  const drawerPlayer = gameState.scores?.find(
    (s) => s.playerId === gameState.drawerId,
  );
  const drawerName = drawerPlayer?.username || "Người chơi";

  return (
    <header className="dg-game-header glass-panel-game !bg-white px-3 sm:px-4 py-2 shrink-0">
      {/* Left: Logo, Room info & Drawer badge */}
      <div className="dg-game-identity flex min-w-0 items-center gap-2 sm:gap-3">
        <span className="dg-game-logo-icon grid place-items-center w-8 h-8 rounded-full bg-sky-100 border-2 border-[#15375f] text-base shrink-0">🎨</span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-x-1.5 gap-y-1">
            <span className="dg-brand-name text-sm sm:text-lg leading-none block">
              Dopamine<span className="dg-brand-dot">.io</span>
            </span>
            {roomId && (
              <span className="text-[9px] font-black text-sky-700 bg-sky-100 border border-sky-200 rounded-md px-1.5 uppercase tracking-widest hidden sm:inline">
                #{roomId}
              </span>
            )}
          </div>
          <div className="mt-0.5">
            {isDrawer ? (
              <span className="text-[10px] sm:text-[11px] font-black text-amber-700 flex items-center gap-1">
                <span>✏️</span> Bạn đang vẽ
              </span>
            ) : (
              <span className="text-[10px] sm:text-[11px] font-extrabold text-sky-700 flex items-center gap-1 min-w-0">
                <span aria-hidden="true">🎨</span>
                <span className="truncate text-slate-800 font-black" title={drawerName}>{drawerName}</span>
                <span className="shrink-0">đang vẽ</span>
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="dg-game-round bg-[#15375f] border-2 border-[#15375f] text-white font-black text-[10px] sm:text-xs px-2 sm:px-3 py-1 rounded-full shadow-md whitespace-nowrap">
          VÒNG {gameState.currentRound} / {gameState.totalRounds}
      </div>

      <div className="dg-game-word min-w-0">
        {isDrawer && phase !== "ROUND_RECAP" ? (
          <SecretWord secretWord={gameState.secretWord || "???"} />
        ) : (
          <WordHint hint={gameState.hint} />
        )}
      </div>

      {/* Right: Round Timer */}
      <div className="dg-game-timer whitespace-nowrap">
        <RoundTimer
          roundEndsAt={timerDeadline}
          totalDurationSeconds={timerDuration}
        />
      </div>
      {actions && <div className="dg-game-actions flex items-center justify-end gap-1.5 sm:gap-2">{actions}</div>}
    </header>
  );
};
