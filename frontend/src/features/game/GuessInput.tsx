import React, { useState, useRef, useEffect } from 'react';
import { wsClient } from '../../websocket/WebSocketClient';
import { MessageType } from '../../websocket/protocol';
import { usePlayerStore } from '../../store/playerStore';
import { useGuessStore, guessStore, GuessEntry, GuessResultStatus } from '../../store/guessStore';

interface GuessInputProps {
  roomId: string;
  disabled?: boolean;
  /** Server-authoritative flag: this player already guessed correctly this round. */
  hasGuessed?: boolean;
}

const isOwnPending = (entry: GuessEntry) => entry.result === undefined && !entry.isCorrect;

/** Renders a locally-submitted guess annotated with the private server result. */
const GuessFeedback: React.FC<{ entry: GuessEntry }> = ({ entry }) => {
  switch (entry.result) {
    case 'CORRECT':
      return (
        <div className="p-1.5 rounded-xl bg-emerald-500/25 border border-emerald-400/50 text-emerald-200 text-xs font-black shadow-sm flex items-center gap-1.5">
          <span className="text-emerald-300">✓</span>
          <span>Chính xác! +{entry.scoreDelta ?? 0} điểm</span>
        </div>
      );
    case 'CLOSE':
      return (
        <div className="p-1.5 rounded-xl bg-amber-500/20 border border-amber-400/50 text-amber-200 text-xs font-bold shadow-sm flex items-center gap-1.5">
          <span className="text-amber-300">~</span>
          <span>
            <span className="font-mono">"{entry.guess}"</span> gần đúng!
          </span>
        </div>
      );
    case 'WRONG':
      return (
        <div className="p-1.5 rounded-xl bg-rose-500/15 border border-rose-400/40 text-rose-200 text-xs font-bold flex items-center gap-1.5">
          <span className="text-rose-300">✕</span>
          <span>
            <span className="font-mono">"{entry.guess}"</span> chưa chính xác.
          </span>
        </div>
      );
    case 'ALREADY_GUESSED':
      return (
        <div className="p-1.5 rounded-xl bg-emerald-500/15 border border-emerald-400/40 text-emerald-200/90 text-xs font-bold flex items-center gap-1.5">
          <span>✓</span>
          <span>Bạn đã đoán đúng rồi!</span>
        </div>
      );
    case 'TIME_EXPIRED':
      return (
        <div className="p-1.5 rounded-xl bg-white/10 border border-white/25 text-blue-100/80 text-xs font-bold flex items-center gap-1.5">
          <span>⏱</span>
          <span>Đã hết thời gian nhận đoán.</span>
        </div>
      );
    case 'ROUND_NOT_ACTIVE':
      return (
        <div className="p-1.5 rounded-xl bg-white/10 border border-white/25 text-blue-100/80 text-xs font-bold flex items-center gap-1.5">
          <span>⏸</span>
          <span>Vòng chơi hiện không còn hoạt động.</span>
        </div>
      );
    case 'ERROR':
      return (
        <div className="p-1.5 rounded-xl bg-rose-500/15 border border-rose-400/40 text-rose-200 text-xs font-bold flex items-center gap-1.5">
          <span>⚠</span>
          <span>Không gửi được đoán, thử lại nhé.</span>
        </div>
      );
    default:
      // Pending — server hasn't answered yet
      return (
        <div className="text-xs font-semibold flex items-center gap-1.5 animate-pulse">
          <span className="text-sky-200 font-bold">{entry.username}:</span>
          <span className="text-white bg-white/15 px-2 py-0.5 rounded-md font-mono">{entry.guess}</span>
          <span className="text-white/50 italic">đang chờ...</span>
        </div>
      );
  }
};

export const GuessInput: React.FC<GuessInputProps> = ({ roomId, disabled, hasGuessed }) => {
  const { playerId, username } = usePlayerStore((s) => s);
  const guesses = useGuessStore((s) => s.guesses);
  const [guess, setGuess] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [guesses]);

  const inputDisabled = disabled || hasGuessed;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!guess.trim() || inputDisabled || submitting) return;

    const val = guess.trim();
    setGuess('');
    setSubmitting(true);

    // Record local guess immediately in guess stream
    const entryId = `guess_${Date.now()}_${Math.random()}`;
    guessStore.addGuess({
      id: entryId,
      roomId,
      playerId,
      username: username || 'Bạn',
      guess: val,
      timestamp: Date.now(),
    });

    try {
      // GUESS_RESULT comes back privately to this player (correlated by requestId)
      const response = await wsClient.send(MessageType.SUBMIT_GUESS, {
        roomId,
        playerId,
        username,
        guess: val,
      });
      guessStore.updateGuess(entryId, {
        result: (response.status as GuessResultStatus) || 'WRONG',
        scoreDelta: response.scoreAwarded ?? 0,
        isCorrect: response.status === 'CORRECT',
      });
    } catch (err: any) {
      console.error('Submit guess error:', err);
      guessStore.updateGuess(entryId, { result: 'ERROR' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="glass-panel-game h-full flex flex-col overflow-hidden select-none">
      {/* Header */}
      <div className="bg-indigo-900/70 px-3 py-1.5 font-extrabold text-[11px] sm:text-xs text-white border-b border-white/20 flex items-center justify-between shrink-0 shadow-sm">
        <span className="flex items-center gap-1.5">
          <span>🎯</span> TRẢ LỜI / ĐOÁN TỪ
        </span>
        <span className="text-[10px] font-bold text-emerald-300">
          {hasGuessed
            ? 'Bạn đã đoán đúng 🎉'
            : disabled
              ? 'Bạn đang vẽ'
              : 'Nhập từ dự đoán'}
        </span>
      </div>

      {/* Guesses Feed - Only displays guess attempts and correct guess notifications */}
      <div className="flex-1 min-h-0 overflow-y-auto p-2.5 space-y-1.5 custom-scrollbar bg-black/10 text-xs">
        {guesses.length === 0 ? (
          <div className="h-full flex items-center justify-center text-xs text-white/50 italic text-center">
            {disabled ? 'Người chơi khác đang suy nghĩ để đoán...' : 'Hãy nhập từ bạn đoán vào khung bên dưới!'}
          </div>
        ) : (
          guesses.map((entry) => {
            // Own submission with a private result — render explicit feedback
            if (entry.result !== undefined) {
              return <GuessFeedback key={entry.id} entry={entry} />;
            }

            // Public broadcast: another player guessed correctly (answer is never leaked)
            if (entry.isCorrect) {
              return (
                <div
                  key={entry.id}
                  className="p-1.5 rounded-xl bg-emerald-500/25 border border-emerald-400/50 text-emerald-200 text-xs font-black shadow-sm flex items-center gap-1.5"
                >
                  <span className="text-emerald-300">✓</span>
                  <span>{entry.username} {entry.guess}</span>
                </div>
              );
            }

            // Own guess still pending server evaluation
            if (isOwnPending(entry)) {
              return <GuessFeedback key={entry.id} entry={entry} />;
            }

            return (
              <div key={entry.id} className="text-xs font-semibold flex items-center gap-1.5">
                <span className="text-sky-200 font-bold">{entry.username}:</span>
                <span className="text-white bg-white/15 px-2 py-0.5 rounded-md font-mono">{entry.guess}</span>
              </div>
            );
          })
        )}
        <div ref={scrollRef} />
      </div>

      {/* Input Form */}
      <form onSubmit={handleSubmit} className="p-2 bg-slate-900/30 border-t border-white/15 flex gap-1.5 shrink-0">
        <input
          type="text"
          disabled={inputDisabled}
          placeholder={
            hasGuessed
              ? 'Bạn đã đoán đúng rồi! 🎉'
              : disabled
                ? 'Người vẽ không được đoán...'
                : 'Lượt của bạn... (Nhập từ dự đoán)'
          }
          value={guess}
          onChange={(e) => setGuess(e.target.value)}
          className="flex-1 px-3 py-1.5 bg-white/90 text-slate-800 rounded-xl text-xs outline-none border border-transparent focus:border-emerald-500 font-bold placeholder:text-slate-400 disabled:opacity-50"
        />
        <button
          type="submit"
          disabled={inputDisabled || !guess.trim() || submitting}
          className="px-4 py-1.5 bg-emerald-500 hover:bg-emerald-600 text-white font-extrabold text-xs rounded-xl shadow-md transition-all disabled:opacity-40"
        >
          {submitting ? '...' : 'ĐOÁN'}
        </button>
      </form>
    </div>
  );
};
