import React from 'react';
import { useAudio } from '../audio/useAudio';

interface SoundToggleProps {
  className?: string;
  size?: 'sm' | 'md' | 'lg';
}

export const SoundToggle: React.FC<SoundToggleProps> = ({
  className = '',
  size = 'md',
}) => {
  const { isMuted, toggleMute } = useAudio();

  const sizeClasses = {
    sm: 'p-1.5 rounded-xl text-base',
    md: 'p-2 sm:p-2.5 rounded-2xl text-lg sm:text-xl',
    lg: 'p-3 rounded-2xl text-2xl',
  };

  return (
    <button
      type="button"
      onClick={toggleMute}
      title={isMuted ? 'Bật âm thanh (Unmute)' : 'Tắt âm thanh (Mute)'}
      aria-label={isMuted ? 'Bật âm thanh' : 'Tắt âm thanh'}
      className={`bouncy-btn transition-all backdrop-blur-md shadow-md flex items-center justify-center ${
        isMuted
          ? 'bg-rose-500/25 hover:bg-rose-500/40 text-rose-200 border border-rose-400/50'
          : 'bg-white/20 hover:bg-white/30 text-white border border-white/30'
      } ${sizeClasses[size]} ${className}`}
    >
      <span className="material-symbols-outlined leading-none">
        {isMuted ? 'volume_off' : 'volume_up'}
      </span>
    </button>
  );
};
