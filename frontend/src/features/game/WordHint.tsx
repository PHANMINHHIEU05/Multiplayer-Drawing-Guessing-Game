import React from 'react';

interface WordHintProps {
  hint: string;
}

export const WordHint: React.FC<WordHintProps> = ({ hint }) => {
  return (
    <div className="bg-slate-900/90 border border-white/30 text-white font-extrabold text-xs sm:text-sm px-4 sm:px-6 py-1 rounded-full shadow-inner tracking-widest flex items-center gap-2">
      <span className="text-[10px] text-slate-400 uppercase font-bold hidden sm:inline">Gợi ý:</span>
      <span className="text-sky-300 font-mono tracking-[0.25em]">{hint || '_ _ _ _'}</span>
    </div>
  );
};

