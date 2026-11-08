import React from 'react';
import { Volume2, VolumeX } from 'lucide-react';
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
  const Icon = isMuted ? VolumeX : Volume2;

  const sizeClasses = {
    sm: 'p-1.5 rounded-lg text-base',
    md: 'p-2 sm:p-2.5 rounded-xl text-lg sm:text-xl',
    lg: 'p-3 rounded-xl text-2xl',
  };

  return (
    <button
      type="button"
      onClick={toggleMute}
      title={isMuted ? 'Bật âm thanh (Unmute)' : 'Tắt âm thanh (Mute)'}
      aria-label={isMuted ? 'Bật âm thanh' : 'Tắt âm thanh'}
      className={`bouncy-btn transition-all border-2 border-[#15375f] shadow-[0_3px_0_rgba(10,52,93,.38)] flex items-center justify-center ${
        isMuted
          ? 'bg-rose-100 hover:bg-rose-200 text-rose-600'
          : 'bg-white hover:bg-sky-50 text-[#15375f]'
      } ${sizeClasses[size]} ${className}`}
    >
      <Icon className="w-[1em] h-[1em] shrink-0" aria-hidden="true" />
    </button>
  );
};
