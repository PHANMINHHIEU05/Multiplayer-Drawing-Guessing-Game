import React from 'react';
import { useConnectionStore } from '../store/connectionStore';
import { wsClient } from '../websocket/WebSocketClient';

export const ConnectionStatus: React.FC = () => {
  const { status, lastError } = useConnectionStore((state) => state);

  const getStatusBadge = () => {
    switch (status) {
      case 'CONNECTED':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            Connected
          </span>
        );
      case 'CONNECTING':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/20 text-amber-400 border border-amber-500/30">
            <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
            Connecting...
          </span>
        );
      case 'RECONNECTING':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-orange-500/20 text-orange-400 border border-orange-500/30">
            <span className="w-2 h-2 rounded-full bg-orange-400 animate-bounce" />
            Reconnecting...
          </span>
        );
      case 'DISCONNECTED':
      default:
        return (
          <div className="inline-flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-rose-500/20 text-rose-400 border border-rose-500/30">
              <span className="w-2 h-2 rounded-full bg-rose-500" />
              Disconnected
            </span>
            <button
              onClick={() => wsClient.connect()}
              className="text-xs bg-indigo-600 hover:bg-indigo-500 text-white px-2.5 py-1 rounded-md transition-all shadow-sm"
            >
              Retry
            </button>
          </div>
        );
    }
  };

  return (
    <div className="flex items-center justify-between">
      {getStatusBadge()}
      {lastError && (
        <span className="text-xs text-rose-400 max-w-xs truncate" title={lastError}>
          {lastError}
        </span>
      )}
    </div>
  );
};
