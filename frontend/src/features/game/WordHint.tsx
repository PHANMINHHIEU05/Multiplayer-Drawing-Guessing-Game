import React from 'react';

interface WordHintProps {
  hint: string;
}

export const WordHint: React.FC<WordHintProps> = ({ hint }) => {
  return (
    <div className="bg-sky-50 border-2 border-[#15375f] text-slate-700 font-extrabold text-xs sm:text-sm px-3 sm:px-5 py-1 rounded-full shadow-inner tracking-widest flex items-center gap-2">
      <span className="text-[10px] text-slate-500 uppercase font-bold hidden sm:inline">Gợi ý:</span>
      <span className="text-sky-700 font-mono tracking-[0.25em]">{hint || '_ _ _ _'}</span>
    </div>
  );
};
