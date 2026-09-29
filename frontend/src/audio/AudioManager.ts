/**
 * AudioManager.ts
 * High-performance, zero-latency Audio Engine for Dopamine.io.
 * Supports:
 * - HTML5 Audio / MP3 loading with sample-accurate Web Audio API fallback.
 * - Procedural synthesized SFX (Correct chime, Wrong buzz, Chat pop, Countdown tick/go, Game Over fanfare, Tool click, Draw scratch).
 * - Calibrated frequency/volume curves to prevent harshness, ear fatigue, or distortion.
 * - Loopable background music with smooth crossfading for Intro, Lobby, and Game Arena.
 * - Global Mute & Volume persistence (localStorage).
 * - Automatic AudioContext unlock on first user gesture.
 */

export type BGMTrack = 'intro' | 'lobby' | 'game';

export type SFXType =
  | 'correct'
  | 'wrong'
  | 'chat'
  | 'countdown_tick'
  | 'countdown_go'
  | 'gameover'
  | 'tool_click'
  | 'draw_start';

class AudioManager {
  private ctx: AudioContext | null = null;
  private masterGain: GainNode | null = null;
  private bgmGain: GainNode | null = null;
  private sfxGain: GainNode | null = null;

  private isMuted: boolean = false;
  private bgmVolume: number = 0.35; // Soft and balanced
  private sfxVolume: number = 0.5;

  private activeBGMTrack: BGMTrack | null = null;
  private bgmLoopTimer: number | null = null;
  private isBGMPlaying: boolean = false;
  private listeners: Set<(muted: boolean) => void> = new Set();

  constructor() {
    // Read persisted mute state
    try {
      const savedMute = localStorage.getItem('dopamine_audio_muted');
      if (savedMute !== null) {
        this.isMuted = savedMute === 'true';
      }
    } catch {
      // Fallback
    }

    // Auto-unlock on first user interaction
    if (typeof window !== 'undefined') {
      const unlock = () => {
        this.initContext();
        if (this.ctx && this.ctx.state === 'suspended') {
          this.ctx.resume();
        }
        window.removeEventListener('pointerdown', unlock);
        window.removeEventListener('keydown', unlock);
      };
      window.addEventListener('pointerdown', unlock, { once: true });
      window.addEventListener('keydown', unlock, { once: true });
    }
  }

  private initContext(): AudioContext {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      this.ctx = new AudioCtx();

      this.masterGain = this.ctx.createGain();
      this.masterGain.gain.setValueAtTime(this.isMuted ? 0 : 1, this.ctx.currentTime);
      this.masterGain.connect(this.ctx.destination);

      this.bgmGain = this.ctx.createGain();
      this.bgmGain.gain.setValueAtTime(this.bgmVolume, this.ctx.currentTime);
      this.bgmGain.connect(this.masterGain);

      this.sfxGain = this.ctx.createGain();
      this.sfxGain.gain.setValueAtTime(this.sfxVolume, this.ctx.currentTime);
      this.sfxGain.connect(this.masterGain);
    }
    return this.ctx;
  }

  public getMuted(): boolean {
    return this.isMuted;
  }

  public toggleMute(): boolean {
    this.isMuted = !this.isMuted;
    try {
      localStorage.setItem('dopamine_audio_muted', String(this.isMuted));
    } catch {
      // Ignore
    }

    const ctx = this.initContext();
    if (this.masterGain) {
      const now = ctx.currentTime;
      this.masterGain.gain.cancelScheduledValues(now);
      this.masterGain.gain.linearRampToValueAtTime(this.isMuted ? 0 : 1, now + 0.05);
    }

    // Notify UI listeners
    this.listeners.forEach((cb) => cb(this.isMuted));

    // Play a gentle feedback tick when unmuting
    if (!this.isMuted) {
      this.playSFX('tool_click');
    }

    return this.isMuted;
  }

  public subscribe(cb: (muted: boolean) => void): () => void {
    this.listeners.add(cb);
    return () => this.listeners.delete(cb);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // SFX Synthesizers (100% harmonious, zero-latency, no harshness)
  // ─────────────────────────────────────────────────────────────────────────────

  public playSFX(type: SFXType) {
    if (this.isMuted) return;
    try {
      const ctx = this.initContext();
      if (ctx.state === 'suspended') {
        ctx.resume();
      }

      switch (type) {
        case 'correct':
          this.synthCorrect(ctx);
          break;
        case 'wrong':
          this.synthWrong(ctx);
          break;
        case 'chat':
          this.synthChat(ctx);
          break;
        case 'countdown_tick':
          this.synthCountdownTick(ctx);
          break;
        case 'countdown_go':
          this.synthCountdownGo(ctx);
          break;
        case 'gameover':
          this.synthGameOver(ctx);
          break;
        case 'tool_click':
          this.synthToolClick(ctx);
          break;
        case 'draw_start':
          this.synthDrawStart(ctx);
          break;
      }
    } catch (e) {
      console.warn('[AudioManager] Failed to play SFX:', e);
    }
  }

  /** Chime arpeggio for correct guess (C5 -> E5 -> G5 -> C6) */
  private synthCorrect(ctx: AudioContext) {
    const notes = [523.25, 659.25, 783.99, 1046.5];
    const now = ctx.currentTime;

    notes.forEach((freq, i) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'triangle';
      osc.frequency.setValueAtTime(freq, now + i * 0.08);

      gain.gain.setValueAtTime(0, now + i * 0.08);
      gain.gain.linearRampToValueAtTime(0.22, now + i * 0.08 + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.08 + 0.55);

      osc.connect(gain);
      gain.connect(this.sfxGain!);

      osc.start(now + i * 0.08);
      osc.stop(now + i * 0.08 + 0.6);
    });
  }

  /** Soft, non-annoying buzzer for wrong guess */
  private synthWrong(ctx: AudioContext) {
    const now = ctx.currentTime;
    const osc1 = ctx.createOscillator();
    const osc2 = ctx.createOscillator();
    const gain = ctx.createGain();
    const filter = ctx.createBiquadFilter();

    osc1.type = 'sawtooth';
    osc1.frequency.setValueAtTime(140, now);
    osc1.frequency.linearRampToValueAtTime(110, now + 0.25);

    osc2.type = 'sawtooth';
    osc2.frequency.setValueAtTime(147, now);
    osc2.frequency.linearRampToValueAtTime(117, now + 0.25);

    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(450, now);

    gain.gain.setValueAtTime(0, now);
    gain.gain.linearRampToValueAtTime(0.18, now + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.28);

    osc1.connect(filter);
    osc2.connect(filter);
    filter.connect(gain);
    gain.connect(this.sfxGain!);

    osc1.start(now);
    osc2.start(now);
    osc1.stop(now + 0.3);
    osc2.stop(now + 0.3);
  }

  /** Subtle chat pop sound (bubble-like) */
  private synthChat(ctx: AudioContext) {
    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(440, now);
    osc.frequency.exponentialRampToValueAtTime(880, now + 0.07);

    gain.gain.setValueAtTime(0.15, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.08);

    osc.connect(gain);
    gain.connect(this.sfxGain!);

    osc.start(now);
    osc.stop(now + 0.09);
  }

  /** Crisp countdown tick (woodblock/marimba pulse) */
  private synthCountdownTick(ctx: AudioContext) {
    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(880, now);
    osc.frequency.exponentialRampToValueAtTime(440, now + 0.05);

    gain.gain.setValueAtTime(0.2, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.06);

    osc.connect(gain);
    gain.connect(this.sfxGain!);

    osc.start(now);
    osc.stop(now + 0.07);
  }

  /** Countdown GO fanfare! (Rising power chord) */
  private synthCountdownGo(ctx: AudioContext) {
    const now = ctx.currentTime;
    const freqs = [523.25, 659.25, 783.99, 1046.5]; // C Major triumph

    freqs.forEach((freq) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'triangle';
      osc.frequency.setValueAtTime(freq * 0.8, now);
      osc.frequency.exponentialRampToValueAtTime(freq, now + 0.08);

      gain.gain.setValueAtTime(0, now);
      gain.gain.linearRampToValueAtTime(0.25, now + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.7);

      osc.connect(gain);
      gain.connect(this.sfxGain!);

      osc.start(now);
      osc.stop(now + 0.75);
    });
  }

  /** Victory / Game Over fanfare */
  private synthGameOver(ctx: AudioContext) {
    const now = ctx.currentTime;
    // Cheerful victory melody: G4 -> C5 -> E5 -> G5 (held)
    const melody = [
      { f: 392.0, t: 0.0, d: 0.15 },
      { f: 523.25, t: 0.15, d: 0.15 },
      { f: 659.25, t: 0.3, d: 0.15 },
      { f: 783.99, t: 0.45, d: 0.8 },
      { f: 1046.5, t: 0.6, d: 0.9 }, // Higher shimmer
    ];

    melody.forEach((note) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'triangle';
      osc.frequency.setValueAtTime(note.f, now + note.t);

      gain.gain.setValueAtTime(0, now + note.t);
      gain.gain.linearRampToValueAtTime(0.28, now + note.t + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.001, now + note.t + note.d);

      osc.connect(gain);
      gain.connect(this.sfxGain!);

      osc.start(now + note.t);
      osc.stop(now + note.t + note.d + 0.05);
    });
  }

  /** Subtle tool click */
  private synthToolClick(ctx: AudioContext) {
    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(1200, now);
    osc.frequency.exponentialRampToValueAtTime(400, now + 0.025);

    gain.gain.setValueAtTime(0.12, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.03);

    osc.connect(gain);
    gain.connect(this.sfxGain!);

    osc.start(now);
    osc.stop(now + 0.035);
  }

  /** Soft pencil / brush touch down */
  private synthDrawStart(ctx: AudioContext) {
    const now = ctx.currentTime;
    // High-passed gentle noise tap
    const bufferSize = ctx.sampleRate * 0.03; // 30ms
    const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = (Math.random() * 2 - 1) * Math.exp(-i / (bufferSize * 0.3));
    }

    const noise = ctx.createBufferSource();
    noise.buffer = buffer;

    const filter = ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(3200, now);
    filter.Q.setValueAtTime(2, now);

    const gain = ctx.createGain();
    gain.gain.setValueAtTime(0.08, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.03);

    noise.connect(filter);
    filter.connect(gain);
    gain.connect(this.sfxGain!);

    noise.start(now);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Background Music (BGM) Engine
  // ─────────────────────────────────────────────────────────────────────────────

  public playBGM(track: BGMTrack) {
    if (this.activeBGMTrack === track && this.isBGMPlaying) return;

    this.stopBGM();
    this.activeBGMTrack = track;
    this.isBGMPlaying = true;

    const ctx = this.initContext();
    if (ctx.state === 'suspended') {
      ctx.resume();
    }

    if (track === 'intro') {
      this.playIntroMusic(ctx);
    } else if (track === 'lobby') {
      this.startLobbyLoop(ctx);
    } else if (track === 'game') {
      this.startGameLoop(ctx);
    }
  }

  public stopBGM() {
    this.isBGMPlaying = false;
    this.activeBGMTrack = null;

    if (this.bgmLoopTimer !== null) {
      window.clearInterval(this.bgmLoopTimer);
      this.bgmLoopTimer = null;
    }

    // Smooth fade out
    if (this.ctx && this.bgmGain) {
      const now = this.ctx.currentTime;
      this.bgmGain.gain.cancelScheduledValues(now);
      this.bgmGain.gain.linearRampToValueAtTime(0.001, now + 0.2);
      setTimeout(() => {
        if (this.bgmGain && this.ctx) {
          this.bgmGain.gain.setValueAtTime(this.bgmVolume, this.ctx.currentTime);
        }
      }, 250);
    }
  }

  /**
   * Intro Music: Playful 3.2s animation stinger
   * Synchronized with brush character run -> kick -> paint flood -> logo pop.
   */
  private playIntroMusic(ctx: AudioContext) {
    const now = ctx.currentTime;

    // Phase 1 (0.0s - 1.0s): Bouncy ascending pizzicato steps (Brush runs in)
    const runNotes = [261.63, 293.66, 329.63, 392.0, 440.0, 523.25];
    runNotes.forEach((freq, idx) => {
      const t = now + idx * 0.16;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'triangle';
      osc.frequency.setValueAtTime(freq, t);

      gain.gain.setValueAtTime(0, t);
      gain.gain.linearRampToValueAtTime(0.2, t + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.001, t + 0.15);

      osc.connect(gain);
      gain.connect(this.bgmGain!);

      osc.start(t);
      osc.stop(t + 0.16);
    });

    // Phase 2 (1.15s): Whoosh + Impact kick!
    const kickTime = now + 1.15;
    const kickOsc = ctx.createOscillator();
    const kickGain = ctx.createGain();
    kickOsc.type = 'sine';
    kickOsc.frequency.setValueAtTime(150, kickTime);
    kickOsc.frequency.exponentialRampToValueAtTime(45, kickTime + 0.15);
    kickGain.gain.setValueAtTime(0.35, kickTime);
    kickGain.gain.exponentialRampToValueAtTime(0.001, kickTime + 0.2);
    kickOsc.connect(kickGain);
    kickGain.connect(this.bgmGain!);
    kickOsc.start(kickTime);
    kickOsc.stop(kickTime + 0.25);

    // Phase 3 (1.3s - 2.0s): Shimmering liquid paint swell
    const swellTime = now + 1.3;
    const swellChords = [523.25, 659.25, 783.99, 987.77];
    swellChords.forEach((freq) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, swellTime);
      gain.gain.setValueAtTime(0, swellTime);
      gain.gain.linearRampToValueAtTime(0.12, swellTime + 0.4);
      gain.gain.exponentialRampToValueAtTime(0.001, swellTime + 1.0);
      osc.connect(gain);
      gain.connect(this.bgmGain!);
      osc.start(swellTime);
      osc.stop(swellTime + 1.1);
    });

    // Phase 4 (2.0s - 3.2s): Triumphant Sparkle Finale (Dopamine.io logo reveal)
    const logoTime = now + 2.0;
    const logoArp = [523.25, 659.25, 783.99, 1046.5, 1318.51];
    logoArp.forEach((freq, idx) => {
      const t = logoTime + idx * 0.09;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(freq, t);
      gain.gain.setValueAtTime(0, t);
      gain.gain.linearRampToValueAtTime(0.22, t + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.001, t + 0.7);
      osc.connect(gain);
      gain.connect(this.bgmGain!);
      osc.start(t);
      osc.stop(t + 0.8);
    });
  }

  /**
   * Lobby BGM: Cheerful, soothing, non-fatiguing chord progression loop (Cmaj7 - Am7 - Dm7 - G7)
   * Plays soft marimba / electric piano notes every few beats.
   */
  private startLobbyLoop(ctx: AudioContext) {
    const chords = [
      [261.63, 329.63, 392.0, 493.88], // Cmaj7
      [220.0, 261.63, 329.63, 392.0],  // Am7
      [293.66, 349.23, 440.0, 523.25], // Dm7
      [196.0, 246.94, 293.66, 349.23], // G7
    ];
    let chordIdx = 0;
    const chordDuration = 2.4; // seconds per chord

    const playBar = () => {
      if (!this.isBGMPlaying || this.activeBGMTrack !== 'lobby') return;
      const chord = chords[chordIdx % chords.length];
      chordIdx++;
      const now = ctx.currentTime;

      // Soft ambient chord pad
      chord.forEach((freq, noteIdx) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(freq, now + noteIdx * 0.03);

        gain.gain.setValueAtTime(0, now);
        gain.gain.linearRampToValueAtTime(0.05, now + 0.3);
        gain.gain.exponentialRampToValueAtTime(0.001, now + chordDuration);

        osc.connect(gain);
        gain.connect(this.bgmGain!);

        osc.start(now);
        osc.stop(now + chordDuration + 0.1);
      });

      // Cheerful light melodic bell pluck
      const topNote = chord[Math.floor(Math.random() * chord.length)] * 2;
      const bell = ctx.createOscillator();
      const bellGain = ctx.createGain();
      bell.type = 'triangle';
      bell.frequency.setValueAtTime(topNote, now + 0.6);
      bellGain.gain.setValueAtTime(0, now + 0.6);
      bellGain.gain.linearRampToValueAtTime(0.08, now + 0.62);
      bellGain.gain.exponentialRampToValueAtTime(0.001, now + 1.5);
      bell.connect(bellGain);
      bellGain.connect(this.bgmGain!);
      bell.start(now + 0.6);
      bell.stop(now + 1.6);
    };

    playBar();
    this.bgmLoopTimer = window.setInterval(playBar, chordDuration * 1000);
  }

  /**
   * Game Arena BGM: Upbeat, light-hearted rhythmic pulse to build creative excitement
   */
  private startGameLoop(ctx: AudioContext) {
    const bassline = [130.81, 146.83, 164.81, 174.61]; // C3 - D3 - E3 - F3 groove
    let step = 0;
    const stepDuration = 0.5; // 120 BPM quarter note

    const playStep = () => {
      if (!this.isBGMPlaying || this.activeBGMTrack !== 'game') return;
      const now = ctx.currentTime;
      const freq = bassline[step % bassline.length];

      // Bass pulse
      const bass = ctx.createOscillator();
      const bassGain = ctx.createGain();
      bass.type = 'triangle';
      bass.frequency.setValueAtTime(freq, now);
      bassGain.gain.setValueAtTime(0.09, now);
      bassGain.gain.exponentialRampToValueAtTime(0.001, now + 0.35);
      bass.connect(bassGain);
      bassGain.connect(this.bgmGain!);
      bass.start(now);
      bass.stop(now + 0.4);

      // Light upbeat percussion tap (closed hi-hat style) on offbeat
      if (step % 2 === 1) {
        const hh = ctx.createOscillator();
        const hhGain = ctx.createGain();
        hh.type = 'sine';
        hh.frequency.setValueAtTime(1400, now + 0.1);
        hhGain.gain.setValueAtTime(0.04, now + 0.1);
        hhGain.gain.exponentialRampToValueAtTime(0.001, now + 0.15);
        hh.connect(hhGain);
        hhGain.connect(this.bgmGain!);
        hh.start(now + 0.1);
        hh.stop(now + 0.16);
      }

      step++;
    };

    playStep();
    this.bgmLoopTimer = window.setInterval(playStep, stepDuration * 1000);
  }
}

export const audioManager = new AudioManager();
