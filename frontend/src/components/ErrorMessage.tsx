import React from 'react';
import { useConnectionStore, connectionStore } from '../store/connectionStore';

export const ErrorMessage: React.FC = () => {
  const lastError = useConnectionStore((state) => state.lastError);

  if (!lastError) return null;

  return (
    <div className="fixed bottom-4 left-4 right-4 sm:left-auto sm:right-4 max-w-sm glass-panel bg-rose-500/90 text-white px-4 py-3 rounded-2xl shadow-2xl backdrop-blur-md flex items-center justify-between gap-3 z-50 animate-bounce">
      <div className="flex items-center gap-2 text-xs font-bold">
        <span className="text-base">⚠️</span>
        <span className="leading-snug">{lastError}</span>
      </div>
      <button
        onClick={() => connectionStore.setLastError(null)}
        className="text-white hover:bg-white/20 rounded-full w-6 h-6 flex items-center justify-center font-bold text-xs transition-colors shrink-0"
      >
        ✕
      </button>
    </div>
  );
};

