import React from 'react';

interface WordHintProps {
  hint: string;
}

export const WordHint: React.FC<WordHintProps> = ({ hint }) => {
  return (
    <div className="dg-word-pill bg-sky-50 border-2 border-[#15375f] text-slate-700 font-extrabold text-xs sm:text-sm px-2 sm:px-3 py-1 rounded-xl shadow-inner flex items-center gap-2">
      <span className="dg-word-label text-[10px] text-slate-500 uppercase font-bold shrink-0 whitespace-nowrap">Gợi ý:</span>
      <span className="dg-word-value text-sky-700 font-mono tracking-wider">{hint || '_ _ _ _'}</span>
    </div>
  );
};
