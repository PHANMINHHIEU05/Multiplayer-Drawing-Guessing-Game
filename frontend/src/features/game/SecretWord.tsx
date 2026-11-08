import React from 'react';

interface SecretWordProps {
  secretWord: string;
}

export const SecretWord: React.FC<SecretWordProps> = ({ secretWord }) => {
  return (
    <div className="dg-word-pill bg-amber-50 border-2 border-[#15375f] text-amber-700 font-extrabold text-xs sm:text-sm px-2 sm:px-3 py-1 rounded-xl shadow-inner flex items-center gap-2">
      <span className="dg-word-label text-[10px] text-slate-500 uppercase font-bold shrink-0 whitespace-nowrap">Từ bí mật:</span>
      <span className="dg-word-value text-slate-900 bg-amber-200 px-1.5 py-0.5 rounded-md font-mono">{secretWord}</span>
    </div>
  );
};
