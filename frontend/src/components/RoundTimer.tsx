import React, { useEffect, useState } from 'react';

interface RoundTimerProps {
  roundEndsAt: number;
  totalDurationSeconds?: number;
}

export const RoundTimer: React.FC<RoundTimerProps> = ({ roundEndsAt, totalDurationSeconds = 60 }) => {
  const [timeLeft, setTimeLeft] = useState<number>(0);

  useEffect(() => {
    const calculateTimeLeft = () => {
      const now = Date.now();
      const diff = Math.max(0, Math.ceil((roundEndsAt - now) / 1000));
      setTimeLeft(diff);
    };

    calculateTimeLeft();
    const interval = setInterval(calculateTimeLeft, 1000);
    return () => clearInterval(interval);
  }, [roundEndsAt]);

  const percentage = Math.min(100, Math.max(0, (timeLeft / totalDurationSeconds) * 100));

  return (
    <div className="flex items-center gap-3 bg-slate-900/60 border border-slate-800 rounded-xl px-4 py-2">
      <div className="text-xl font-black text-indigo-400 font-mono w-10 text-center">{timeLeft}s</div>
      <div className="w-32 h-2.5 bg-slate-800 rounded-full overflow-hidden border border-slate-700">
        <div
          className={`h-full transition-all duration-1000 ease-linear ${
            percentage > 50 ? 'bg-indigo-500' : percentage > 20 ? 'bg-amber-500' : 'bg-rose-500 animate-pulse'
          }`}
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
};
