import React, { useEffect } from 'react';
import { useChatStore } from '../../store/chatStore';
import { usePlayerStore } from '../../store/playerStore';
import { wsClient } from '../../websocket/WebSocketClient';
import { MessageType } from '../../websocket/protocol';
import { ChatMessageList } from './ChatMessageList';
import { ChatInput } from './ChatInput';

interface ChatPanelProps {
  roomId: string;
}

export const ChatPanel: React.FC<ChatPanelProps> = ({ roomId }) => {
  const messages = useChatStore((s) => s.messages);
  const { playerId } = usePlayerStore((s) => s);

  useEffect(() => {
    if (roomId) {
      wsClient
        .send(MessageType.GET_RECENT_CHAT, { roomId, playerId, limit: 50 })
        .catch((err) => console.log('Fetch chat history note:', err));
    }
  }, [roomId, playerId]);

  return (
    <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-4 shadow-xl flex flex-col h-full min-h-[380px]">
      <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-3">
        <h3 className="text-sm font-semibold text-slate-200 flex items-center gap-2">
          <span>💬</span> Room Chat
        </h3>
        <span className="text-[10px] text-slate-500 font-mono">{messages.length} msgs</span>
      </div>

      <ChatMessageList messages={messages} currentPlayerId={playerId} />
      <ChatInput roomId={roomId} />
    </div>
  );
};
