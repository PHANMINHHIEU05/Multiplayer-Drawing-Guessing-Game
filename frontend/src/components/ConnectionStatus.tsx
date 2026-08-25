import React from 'react';
import { useConnectionStore } from '../store/connectionStore';
import { wsClient } from '../websocket/WebSocketClient';

export const ConnectionStatus: React.FC = () => {
  const { status, lastError } = useConnectionStore((state) => state);

  const getStatusBadge = () => {
    switch (status) {
      case 'CONNECTED':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-extrabold bg-emerald-500/25 text-emerald-200 border border-emerald-400/40 backdrop-blur-md shadow-sm">
            <span className="w-2 h-2 rounded-full bg-emerald-300 animate-pulse" />
            Online
          </span>
        );
      case 'CONNECTING':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-extrabold bg-amber-500/25 text-amber-200 border border-amber-400/40 backdrop-blur-md shadow-sm">
            <span className="w-2 h-2 rounded-full bg-amber-300 animate-ping" />
            Đang kết nối...
          </span>
        );
      case 'RECONNECTING':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-extrabold bg-orange-500/25 text-orange-200 border border-orange-400/40 backdrop-blur-md shadow-sm">
            <span className="w-2 h-2 rounded-full bg-orange-300 animate-bounce" />
            Đang thử lại...
          </span>
        );
      case 'DISCONNECTED':
      default:
        return (
          <div className="inline-flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-extrabold bg-rose-500/25 text-rose-200 border border-rose-400/40 backdrop-blur-md shadow-sm">
              <span className="w-2 h-2 rounded-full bg-rose-400" />
              Mất kết nối
            </span>
            <button
              onClick={() => wsClient.connect()}
              className="text-xs bg-white/30 hover:bg-white/40 text-white font-bold px-2.5 py-1 rounded-xl transition-all shadow-sm border border-white/40"
            >
              Kết nối lại
            </button>
          </div>
        );
    }
  };

  return (
    <div className="flex items-center gap-2 select-none">
      {getStatusBadge()}
      {lastError && (
        <span className="text-xs text-rose-200 bg-rose-900/60 px-2 py-0.5 rounded-lg max-w-xs truncate" title={lastError}>
          {lastError}
        </span>
      )}
    </div>
  );
};

