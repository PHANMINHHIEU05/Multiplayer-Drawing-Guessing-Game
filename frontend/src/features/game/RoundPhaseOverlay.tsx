import React, { useEffect, useState } from "react";
import { GameState } from "../../types/game";

interface Props {
  gameState: GameState;
  isDrawer: boolean;
  onSelectWord: (choiceId: string) => void;
}

function useSecondsUntil(deadline?: number): number {
  const [seconds, setSeconds] = useState(() => Math.max(0, Math.ceil(((deadline || 0) - Date.now()) / 1000)));
  useEffect(() => {
    const update = () => setSeconds(Math.max(0, Math.ceil(((deadline || 0) - Date.now()) / 1000)));
    update();
    const timer = window.setInterval(update, 200);
    return () => window.clearInterval(timer);
  }, [deadline]);
  return seconds;
}

export const RoundPhaseOverlay: React.FC<Props> = ({ gameState, isDrawer, onSelectWord }) => {
  const phase = gameState.roundPhase || "DRAWING";
  const seconds = useSecondsUntil(phase === "DRAWING" ? gameState.roundEndsAt : gameState.phaseEndsAt);
  const [selected, setSelected] = useState(false);
  useEffect(() => setSelected(false), [gameState.gameId, gameState.currentRound, phase]);

  if (phase === "DRAWING") return null;

  if (phase === "WORD_SELECTION") {
    return (
      <div className="absolute inset-0 z-30 flex items-center justify-center bg-slate-950/35 backdrop-blur-[2px] p-3" role="status">
        <section className="w-full max-w-xl rounded-3xl border border-white/30 bg-slate-900/90 p-5 text-center shadow-2xl sm:p-7">
          <p className="text-xs font-black tracking-[.22em] text-sky-200">CHỌN MỘT TỪ ĐỂ VẼ</p>
          <div className="my-3 text-4xl font-black tabular-nums text-amber-300" aria-label={`${seconds} giây`}>{seconds}s</div>
          {isDrawer ? (
            <div className="grid gap-2 sm:grid-cols-3">
              {(gameState.wordChoices || []).map((choice) => (
                <button key={choice.choiceId} disabled={selected} onClick={() => { setSelected(true); onSelectWord(choice.choiceId); }}
                  className="min-h-16 rounded-2xl border border-sky-200/40 bg-sky-500/20 px-3 py-4 text-base font-black text-white transition hover:-translate-y-0.5 hover:bg-violet-500/40 disabled:cursor-not-allowed disabled:opacity-50">
                  {choice.displayWord}
                </button>
              ))}
              {(gameState.wordChoices || []).length === 0 && <p className="sm:col-span-3 text-sm text-white/70">Đang khôi phục lựa chọn từ…</p>}
            </div>
          ) : <p className="text-sm font-bold text-white/80">Người vẽ đang chọn từ…</p>}
        </section>
      </div>
    );
  }

  if (phase === "COUNTDOWN") {
    return (
      <div className="absolute inset-0 z-30 flex items-center justify-center bg-slate-950/25 pointer-events-none" aria-live="polite">
        <section className="rounded-3xl border border-white/35 bg-slate-900/75 px-10 py-6 text-center shadow-2xl">
          <p className="text-sm font-black tracking-widest text-white">{isDrawer ? "ĐẾN LƯỢT BẠN VẼ" : "LƯỢT MỚI · ĐANG CHUẨN BỊ"}</p>
          {isDrawer && <p className="mt-2 text-lg font-black text-amber-300">{gameState.secretWord}</p>}
          <div key={seconds} className="mt-2 text-7xl font-black tabular-nums text-white motion-safe:animate-pulse">{seconds > 0 ? seconds : "VẼ!"}</div>
        </section>
      </div>
    );
  }

  if (phase === "ROUND_RECAP") {
    const recap = gameState.roundRecap;
    return (
      <div className="absolute inset-0 z-30 flex items-center justify-center bg-slate-950/45 p-3" role="status">
        <section className="w-full max-w-lg rounded-3xl border border-amber-200/40 bg-slate-900/95 p-5 shadow-2xl sm:p-7">
          <div className="text-center">
            <p className="text-xs font-black tracking-[.2em] text-amber-200">HẾT LƯỢT</p>
            <h2 className="mt-1 text-2xl font-black text-white">{recap?.answer || gameState.secretWord || "Đáp án đã được tiết lộ"}</h2>
            <p className="mt-1 text-xs text-white/60">Lượt tiếp theo sau {seconds}s</p>
          </div>
          <div className="mt-4 max-h-44 space-y-1 overflow-y-auto">
            {(recap?.scoreDeltas || []).map((score) => (
              <div key={score.playerId} className="flex items-center justify-between rounded-xl bg-white/5 px-3 py-2 text-sm">
                <span className="font-bold text-white">{score.username}</span>
                <span className="text-right"><b className={score.roundDelta > 0 ? "text-emerald-300" : "text-white/60"}>{score.roundDelta > 0 ? `+${score.roundDelta}` : score.roundDelta}</b><small className="ml-2 text-white/50">Tổng {score.totalScore}</small></span>
              </div>
            ))}
            {recap && (recap.correctPlayerIds || []).length === 0 && <p className="mb-2 text-center text-sm text-white/65">Không ai đoán đúng lượt này.</p>}
          </div>
          {recap?.fastestUsername && <p className="mt-3 text-center text-sm font-bold text-sky-200">⚡ Nhanh nhất: {recap.fastestUsername} · {((recap.fastestElapsedMillis || 0) / 1000).toFixed(1)} giây</p>}
        </section>
      </div>
    );
  }
  return null;
};

export const REACTION_TYPES = ["😂", "👍", "🔥", "😮", "🤔", "❤️"] as const;

export const ReactionBar: React.FC<{ onSend: (reactionType: string) => void; disabled?: boolean }> = ({ onSend, disabled }) => {
  const [expanded, setExpanded] = useState(false);

  const sendReaction = (reaction: string) => {
    onSend(reaction);
    setExpanded(false);
  };

  return (
    <div className="absolute bottom-3 left-3 z-40 flex items-center gap-1.5 rounded-2xl border border-white/25 bg-slate-900/75 p-1.5 shadow-lg backdrop-blur" aria-label="Phản ứng nhanh">
      <button
        type="button"
        disabled={disabled}
        onClick={() => setExpanded((open) => !open)}
        className="grid h-9 w-9 shrink-0 place-items-center rounded-xl text-lg transition hover:bg-white/15 disabled:opacity-40"
        aria-label={expanded ? "Thu gọn cảm xúc" : "Mở cảm xúc"}
        aria-expanded={expanded}
        aria-controls="quick-reactions"
        title={expanded ? "Thu gọn" : "Gửi cảm xúc"}
      >
        {expanded ? "×" : "😊"}
      </button>
      {expanded && (
        <div id="quick-reactions" className="flex items-center gap-1" role="group" aria-label="Chọn cảm xúc">
          {REACTION_TYPES.map((reaction) => (
            <button
              key={reaction}
              type="button"
              disabled={disabled}
              onClick={() => sendReaction(reaction)}
              className="grid h-9 w-9 place-items-center rounded-xl text-lg transition hover:scale-110 hover:bg-white/15 disabled:opacity-40"
              aria-label={`Gửi ${reaction}`}
            >
              {reaction}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
