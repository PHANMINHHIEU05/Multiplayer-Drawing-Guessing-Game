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
    <div className="glass-panel-game h-full flex flex-col overflow-hidden select-none">
      <div className="bg-indigo-950/60 px-3 py-1.5 font-extrabold text-[11px] sm:text-xs text-white border-b border-white/20 flex items-center justify-between shrink-0">
        <span className="flex items-center gap-1.5">
          <span>💬</span> TRÒ CHUYỆN
        </span>
        <span className="text-[10px] text-sky-200 font-bold">{messages.length} tin</span>
      </div>

      <div className="flex-1 min-h-0 flex flex-col p-2 bg-black/10">
        <ChatMessageList messages={messages} currentPlayerId={playerId} />
      </div>
      <ChatInput roomId={roomId} />
    </div>
  );
};

