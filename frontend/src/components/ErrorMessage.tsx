import React from 'react';
import { useConnectionStore, connectionStore } from '../store/connectionStore';

export const ErrorMessage: React.FC = () => {
  const lastError = useConnectionStore((state) => state.lastError);

  if (!lastError) return null;

  return (
    <div className="fixed bottom-4 right-4 max-w-md bg-rose-950/90 border border-rose-500/50 text-rose-200 px-4 py-3 rounded-xl shadow-2xl backdrop-blur-md flex items-center justify-between gap-3 z-50 animate-bounce">
      <div className="flex items-center gap-2">
        <span className="text-rose-400 font-bold text-lg">⚠️</span>
        <span className="text-sm font-medium">{lastError}</span>
      </div>
      <button
        onClick={() => connectionStore.setLastError(null)}
        className="text-rose-400 hover:text-rose-100 font-bold p-1"
      >
        ✕
      </button>
    </div>
  );
};
