import React, { useState } from 'react';
import { wsClient } from '../../websocket/WebSocketClient';
import { MessageType } from '../../websocket/protocol';
import { usePlayerStore } from '../../store/playerStore';

interface ChatInputProps {
  roomId: string;
}

export const ChatInput: React.FC<ChatInputProps> = ({ roomId }) => {
  const { playerId, username } = usePlayerStore((s) => s);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!text.trim() || sending) return;

    const content = text.trim();
    setText('');
    setSending(true);

    try {
      await wsClient.send(MessageType.SEND_CHAT, {
        roomId,
        playerId,
        username,
        content,
      });
    } catch (err: any) {
      console.error('Send chat failed:', err);
    } finally {
      setSending(false);
    }
  };

  return (
    <form onSubmit={handleSend} className="flex gap-2 pt-2 border-t border-slate-800">
      <input
        type="text"
        placeholder="Send a message..."
        value={text}
        onChange={(e) => setText(e.target.value)}
        className="flex-1 px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
      />
      <button
        type="submit"
        disabled={!text.trim() || sending}
        className="px-3 py-2 bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-xs rounded-xl transition-all disabled:opacity-50"
      >
        Send
      </button>
    </form>
  );
};
