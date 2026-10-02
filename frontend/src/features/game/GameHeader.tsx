import React from "react";
import { GameState } from "../../types/game";
import { RoundTimer } from "../../components/RoundTimer";
import { SecretWord } from "./SecretWord";
import { WordHint } from "./WordHint";

interface GameHeaderProps {
  gameState: GameState;
  isDrawer: boolean;
  roomId?: string;
}

export const GameHeader: React.FC<GameHeaderProps> = ({
  gameState,
  isDrawer,
  roomId,
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
    <div className="glass-panel-game !bg-white px-3 sm:px-4 py-2 flex items-center justify-between gap-2 sm:gap-4 shrink-0">
      {/* Left: Logo, Room info & Drawer badge */}
      <div className="flex items-center gap-2 sm:gap-3">
        <span className="grid place-items-center w-8 h-8 rounded-full bg-sky-100 border-2 border-[#15375f] text-base shrink-0">🎨</span>
        <div>
          <div className="flex items-center gap-1.5">
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
              <span className="text-[10px] sm:text-[11px] font-extrabold text-sky-700 flex items-center gap-1">
                <span>🎨</span>{" "}
                <span className="text-slate-800 font-black">{drawerName}</span> đang
                vẽ
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Center: Round & Secret Word / Word Hint */}
      <div className="flex items-center gap-2 sm:gap-4 flex-1 justify-center max-w-lg">
        {/* Round Badge */}
        <div className="bg-[#15375f] border-2 border-[#15375f] text-white font-black text-[10px] sm:text-xs px-2.5 sm:px-3 py-1 rounded-full shadow-md whitespace-nowrap">
          VÒNG {gameState.currentRound} / {gameState.totalRounds}
        </div>

        {/* Word or Hint */}
        {isDrawer && phase !== "ROUND_RECAP" ? (
          <SecretWord secretWord={gameState.secretWord || "???"} />
        ) : (
          <WordHint hint={gameState.hint} />
        )}
      </div>

      {/* Right: Round Timer */}
      <div className="flex items-center gap-2 shrink-0">
        <RoundTimer
          roundEndsAt={timerDeadline}
          totalDurationSeconds={timerDuration}
        />
      </div>
    </div>
  );
};
