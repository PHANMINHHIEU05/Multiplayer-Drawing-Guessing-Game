import React from 'react';

interface SecretWordProps {
  secretWord: string;
}

export const SecretWord: React.FC<SecretWordProps> = ({ secretWord }) => {
  return (
    <div className="flex flex-col items-center bg-indigo-950/60 border border-indigo-500/40 rounded-xl px-5 py-2">
      <span className="text-[10px] uppercase tracking-widest text-indigo-300 font-bold">Your Secret Word</span>
      <span className="text-xl font-black text-amber-300 tracking-wider font-mono">{secretWord}</span>
    </div>
  );
};
