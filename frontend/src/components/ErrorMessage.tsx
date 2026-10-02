import React from 'react';
import { useConnectionStore, connectionStore } from '../store/connectionStore';

export const ErrorMessage: React.FC = () => {
  const lastError = useConnectionStore((state) => state.lastError);

  if (!lastError) return null;

  return (
    <div className="fixed bottom-4 left-4 right-4 sm:left-auto sm:right-4 max-w-sm bg-rose-50 text-rose-950 border-2 border-[#15375f] px-4 py-3 rounded-xl shadow-[0_5px_0_rgba(10,52,93,.35),0_16px_30px_rgba(10,52,93,.2)] flex items-center justify-between gap-3 z-50 animate-pop-in">
      <div className="flex items-center gap-2 text-xs font-bold">
        <span className="text-base">⚠️</span>
        <span className="leading-snug">{lastError}</span>
      </div>
      <button
        onClick={() => connectionStore.setLastError(null)}
        className="text-rose-700/60 hover:text-rose-950 hover:bg-rose-200/60 rounded-full w-6 h-6 flex items-center justify-center font-bold text-xs transition-colors shrink-0"
      >
        ✕
      </button>
    </div>
  );
};
