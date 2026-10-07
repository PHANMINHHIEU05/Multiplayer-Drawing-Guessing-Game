// Runs on the audio thread, independent of drawing/rendering and background-tab timers.
class VoiceNoiseGate extends AudioWorkletProcessor {
  constructor() {
    super();
    this.floor = 0.001;
    this.hold = 0;
    this.gain = 0;
  }

  process(inputs, outputs) {
    const input = inputs[0]?.[0];
    const output = outputs[0]?.[0];
    if (!output) return true;
    if (!input) {
      output.fill(0);
      return true;
    }
    let energy = 0;
    for (const value of input) energy += value * value;
    const rms = Math.sqrt(energy / input.length);
    // Learn quiet background only; never learn speech as the noise floor.
    if (rms < Math.max(0.006, this.floor * 2)) {
      this.floor += (rms - this.floor) * 0.005;
      this.floor = Math.max(0.0003, Math.min(0.006, this.floor));
    }
    const openThreshold = Math.max(0.004, this.floor * 3);
    const closeThreshold = openThreshold * 0.65;
    if (rms > (this.hold > 0 ? closeThreshold : openThreshold)) {
      this.hold = sampleRate * 0.25;
    } else {
      this.hold = Math.max(0, this.hold - input.length);
    }
    const target = this.hold > 0 ? 1 : 0;
    const smoothing = 1 - Math.exp(-1 / (sampleRate * (target ? 0.003 : 0.03)));
    for (let i = 0; i < output.length; i++) {
      this.gain += (target - this.gain) * smoothing;
      output[i] = input[i] * this.gain;
    }
    return true;
  }
}
registerProcessor("voice-noise-gate", VoiceNoiseGate);
