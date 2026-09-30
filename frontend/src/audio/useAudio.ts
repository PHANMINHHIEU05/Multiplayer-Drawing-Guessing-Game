import { useState, useEffect, useCallback } from 'react';
import { audioManager, BGMTrack, SFXType } from './AudioManager';

export function useAudio() {
  const [isMuted, setIsMuted] = useState<boolean>(() => audioManager.getMuted());

  useEffect(() => {
    return audioManager.subscribe((muted) => {
      setIsMuted(muted);
    });
  }, []);

  const toggleMute = useCallback(() => {
    return audioManager.toggleMute();
  }, []);

  const playSFX = useCallback((type: SFXType) => {
    audioManager.playSFX(type);
  }, []);

  const playBGM = useCallback((track: BGMTrack) => {
    audioManager.playBGM(track);
  }, []);

  const stopBGM = useCallback(() => {
    audioManager.stopBGM();
  }, []);

  return {
    isMuted,
    toggleMute,
    playSFX,
    playBGM,
    stopBGM,
  };
}
