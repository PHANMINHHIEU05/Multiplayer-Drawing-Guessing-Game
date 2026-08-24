import React from 'react';

interface SecretWordProps {
  secretWord: string;
}

export const SecretWord: React.FC<SecretWordProps> = ({ secretWord }) => {
  return (
    <div className="bg-slate-900 border-2 border-amber-400 text-amber-300 font-extrabold text-xs sm:text-sm px-4 sm:px-6 py-1 rounded-full shadow-inner tracking-widest flex items-center gap-2">
      <span className="text-[10px] text-slate-400 uppercase font-bold hidden sm:inline">Từ Bí Mật:</span>
      <span className="text-white bg-slate-800 px-2.5 py-0.5 rounded-md font-mono tracking-wider">{secretWord}</span>
    </div>
  );
};

