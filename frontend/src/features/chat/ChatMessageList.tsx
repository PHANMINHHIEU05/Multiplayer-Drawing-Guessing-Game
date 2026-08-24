import React, { useEffect, useRef } from 'react';
import { ChatMessage } from '../../types/chat';

interface ChatMessageListProps {
  messages: ChatMessage[];
  currentPlayerId: string;
}

export const ChatMessageList: React.FC<ChatMessageListProps> = ({ messages, currentPlayerId }) => {
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const formatTime = (ts: number) => {
    return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div className="flex-1 overflow-y-auto space-y-1.5 pr-1 custom-scrollbar min-h-0">
      {messages.length === 0 ? (
        <div className="h-full flex items-center justify-center text-xs text-white/50 italic text-center">
          Chưa có tin nhắn nào trong phòng...
        </div>
      ) : (
        messages.map((msg, index) => {
          const isSystem = msg.type === 'SYSTEM' || msg.playerId === 'system';
          const isCurrent = msg.playerId === currentPlayerId;

          if (isSystem) {
            return (
              <div key={msg.messageId || index} className="text-center my-1">
                <span className="text-[10px] bg-sky-950/60 text-sky-200 px-2.5 py-0.5 rounded-full border border-sky-400/30 font-bold inline-block shadow-sm">
                  {msg.content}
                </span>
              </div>
            );
          }

          return (
            <div
              key={msg.messageId || index}
              className={`flex flex-col ${isCurrent ? 'items-end' : 'items-start'}`}
            >
              <div className="flex items-center gap-1 mb-0.5 text-[9px] text-white/60">
                <span className="font-bold text-white/90">{msg.username}</span>
                <span>{formatTime(msg.createdAt)}</span>
              </div>
              <div
                className={`max-w-[90%] px-2.5 py-1 rounded-xl text-xs leading-snug break-words shadow-sm font-medium ${
                  isCurrent
                    ? 'bg-primary text-white rounded-tr-none'
                    : 'bg-white/20 border border-white/30 text-white rounded-tl-none'
                }`}
              >
                {msg.content}
              </div>
            </div>
          );
        })
      )}
      <div ref={bottomRef} />
    </div>
  );
};

