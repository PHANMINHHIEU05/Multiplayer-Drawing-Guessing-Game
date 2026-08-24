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
    <form onSubmit={handleSend} className="p-2 bg-slate-900/30 border-t border-white/15 flex gap-1.5 shrink-0">
      <input
        type="text"
        placeholder="Nhắn tin trong phòng..."
        value={text}
        onChange={(e) => setText(e.target.value)}
        className="flex-1 px-3 py-1.5 bg-white/90 text-slate-800 rounded-xl text-xs outline-none border border-transparent focus:border-primary font-medium placeholder:text-slate-400"
      />
      <button
        type="submit"
        disabled={!text.trim() || sending}
        className="px-3 py-1.5 bg-primary hover:bg-primary-dark text-white font-extrabold text-xs rounded-xl shadow-md transition-all disabled:opacity-40"
      >
        GỬI
      </button>
    </form>
  );
};

