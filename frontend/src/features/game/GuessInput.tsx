import React, { useState, useRef, useEffect } from 'react';
import { wsClient } from '../../websocket/WebSocketClient';
import { MessageType } from '../../websocket/protocol';
import { usePlayerStore } from '../../store/playerStore';
import { useGuessStore, guessStore } from '../../store/guessStore';

interface GuessInputProps {
  roomId: string;
  disabled?: boolean;
}

export const GuessInput: React.FC<GuessInputProps> = ({ roomId, disabled }) => {
  const { playerId, username } = usePlayerStore((s) => s);
  const guesses = useGuessStore((s) => s.guesses);
  const [guess, setGuess] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [guesses]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!guess.trim() || disabled || submitting) return;

    const val = guess.trim();
    setGuess('');
    setSubmitting(true);

    // Record local guess immediately in guess stream
    guessStore.addGuess({
      id: `guess_${Date.now()}_${Math.random()}`,
      roomId,
      playerId,
      username: username || 'Bạn',
      guess: val,
      timestamp: Date.now(),
    });

    try {
      await wsClient.send(MessageType.SUBMIT_GUESS, {
        roomId,
        playerId,
        username,
        guess: val,
      });
    } catch (err: any) {
      console.error('Submit guess error:', err);
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
        <span className="text-[10px] text-emerald-300 font-bold">
          {disabled ? 'Bạn đang vẽ' : 'Nhập từ dự đoán'}
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
          disabled={disabled}
          placeholder={disabled ? 'Người vẽ không được đoán...' : 'Lượt của bạn... (Nhập từ dự đoán)'}
          value={guess}
          onChange={(e) => setGuess(e.target.value)}
          className="flex-1 px-3 py-1.5 bg-white/90 text-slate-800 rounded-xl text-xs outline-none border border-transparent focus:border-emerald-500 font-bold placeholder:text-slate-400 disabled:opacity-50"
        />
        <button
          type="submit"
          disabled={disabled || !guess.trim() || submitting}
          className="px-4 py-1.5 bg-emerald-500 hover:bg-emerald-600 text-white font-extrabold text-xs rounded-xl shadow-md transition-all disabled:opacity-40"
        >
          {submitting ? '...' : 'ĐOÁN'}
        </button>
      </form>
    </div>
  );
};


