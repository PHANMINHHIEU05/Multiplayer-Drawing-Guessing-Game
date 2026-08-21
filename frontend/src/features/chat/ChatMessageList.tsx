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
    <div className="flex-1 overflow-y-auto space-y-2 pr-1 custom-scrollbar min-h-[260px] max-h-[360px]">
      {messages.length === 0 ? (
        <div className="h-full flex items-center justify-center text-xs text-slate-500 italic">
          No chat messages yet. Say hi!
        </div>
      ) : (
        messages.map((msg, index) => {
          const isSystem = msg.type === 'SYSTEM' || msg.playerId === 'system';
          const isCurrent = msg.playerId === currentPlayerId;

          if (isSystem) {
            return (
              <div key={msg.messageId || index} className="text-center my-1.5">
                <span className="text-[11px] bg-slate-800/80 text-amber-300 px-3 py-1 rounded-full border border-slate-700/60 font-medium inline-block shadow-sm">
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
              <div className="flex items-center gap-1.5 mb-0.5 text-[10px] text-slate-400">
                <span className="font-semibold text-slate-300">{msg.username}</span>
                <span>{formatTime(msg.createdAt)}</span>
              </div>
              <div
                className={`max-w-[85%] px-3 py-2 rounded-2xl text-xs leading-relaxed break-words shadow-sm ${
                  isCurrent
                    ? 'bg-indigo-600 text-white rounded-tr-none'
                    : 'bg-slate-800 border border-slate-700 text-slate-200 rounded-tl-none'
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
