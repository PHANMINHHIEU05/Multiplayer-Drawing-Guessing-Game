import React, { useState } from 'react';
import { wsClient } from '../../websocket/WebSocketClient';
import { MessageType } from '../../websocket/protocol';
import { usePlayerStore } from '../../store/playerStore';

interface GuessInputProps {
  roomId: string;
  disabled?: boolean;
}

export const GuessInput: React.FC<GuessInputProps> = ({ roomId, disabled }) => {
  const { playerId, username } = usePlayerStore((s) => s);
  const [guess, setGuess] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!guess.trim() || disabled || submitting) return;

    const val = guess.trim();
    setGuess('');
    setSubmitting(true);

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
    <form onSubmit={handleSubmit} className="flex gap-2">
      <input
        type="text"
        disabled={disabled}
        placeholder={disabled ? "Drawer can't guess!" : 'Type your guess here...'}
        value={guess}
        onChange={(e) => setGuess(e.target.value)}
        className="flex-1 px-4 py-2.5 bg-slate-900 border border-slate-800 rounded-xl text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-500 disabled:opacity-50 font-medium"
      />
      <button
        type="submit"
        disabled={disabled || !guess.trim() || submitting}
        className="px-5 py-2.5 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-xl shadow-lg shadow-amber-500/20 transition-all disabled:opacity-50"
      >
        Guess
      </button>
    </form>
  );
};
