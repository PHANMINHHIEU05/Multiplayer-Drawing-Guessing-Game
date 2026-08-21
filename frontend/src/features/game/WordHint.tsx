import React from 'react';

interface WordHintProps {
  hint: string;
}

export const WordHint: React.FC<WordHintProps> = ({ hint }) => {
  return (
    <div className="flex flex-col items-center bg-slate-900/60 border border-slate-800 rounded-xl px-5 py-2">
      <span className="text-[10px] uppercase tracking-widest text-slate-400 font-bold">Word Hint</span>
      <span className="text-xl font-black text-indigo-300 tracking-widest font-mono">{hint || '_ _ _ _'}</span>
    </div>
  );
};
