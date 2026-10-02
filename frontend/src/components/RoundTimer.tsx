import React, { useEffect, useState } from 'react';

interface RoundTimerProps {
  roundEndsAt: number;
  totalDurationSeconds?: number;
  showBar?: boolean;
}

export const RoundTimer: React.FC<RoundTimerProps> = ({
  roundEndsAt,
  totalDurationSeconds = 60,
  showBar = false,
}) => {
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
    <div className="flex flex-col items-center gap-1">
      <div className="bg-rose-500 border-2 border-[#15375f] text-white font-black text-xs sm:text-sm px-3.5 py-1 rounded-full shadow-[0_2px_0_rgba(21,55,95,.35)] flex items-center gap-1">
        <span className="material-symbols-outlined text-sm">timer</span>
        <span>{timeLeft}s</span>
      </div>

      {showBar && (
        <div className="w-full h-1.5 bg-slate-200 rounded-full overflow-hidden border border-slate-300">
          <div
            className="h-full bg-gradient-to-r from-amber-400 via-orange-500 to-red-500 rounded-full transition-all duration-1000 ease-linear"
            style={{ width: `${percentage}%` }}
          />
        </div>
      )}
    </div>
  );
};
