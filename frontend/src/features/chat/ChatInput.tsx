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
    <form onSubmit={handleSend} className="p-2 bg-white border-t-2 border-[#b5dbea] flex gap-1.5 shrink-0">
      <input
        type="text"
        placeholder="Nhắn tin trong phòng..."
        value={text}
        onChange={(e) => setText(e.target.value)}
        className="flex-1 min-w-0 px-3 py-2 bg-sky-50 text-slate-800 rounded-lg text-xs outline-none border-2 border-sky-200 focus:border-sky-500 font-medium placeholder:text-slate-400"
      />
      <button
        type="submit"
        disabled={!text.trim() || sending}
        className="dg-primary-button px-3 py-1.5 text-xs disabled:opacity-40"
      >
        GỬI
      </button>
    </form>
  );
};
