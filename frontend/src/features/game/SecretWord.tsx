import React from 'react';

interface SecretWordProps {
  secretWord: string;
}

export const SecretWord: React.FC<SecretWordProps> = ({ secretWord }) => {
  return (
    <div className="bg-amber-50 border-2 border-[#15375f] text-amber-700 font-extrabold text-xs sm:text-sm px-3 sm:px-5 py-1 rounded-full shadow-inner tracking-widest flex items-center gap-2">
      <span className="text-[10px] text-slate-500 uppercase font-bold hidden sm:inline">Từ bí mật:</span>
      <span className="text-slate-900 bg-amber-200 px-2.5 py-0.5 rounded-md font-mono tracking-wider">{secretWord}</span>
    </div>
  );
};
