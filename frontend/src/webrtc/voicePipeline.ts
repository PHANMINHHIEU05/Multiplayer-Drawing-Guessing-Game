export interface VoicePipeline {
  stream: MediaStream;
  dispose: () => void;
}

/** Native echo/noise suppression runs before this graph. Never monitor the mic locally. */
export async function createVoicePipeline(input: MediaStream, preparedContext?: AudioContext): Promise<VoicePipeline> {
  const context = preparedContext ?? new AudioContext({ latencyHint: "interactive" });
  try {
    // A denied/suspended Web Audio context can leave resume pending forever.
    // Fall back to native microphone processing instead of hanging "Bật Mic".
    let resumeTimeout: ReturnType<typeof setTimeout> | undefined;
    try {
      await Promise.race([
        context.resume(),
        new Promise<never>((_, reject) => {
          resumeTimeout = setTimeout(() => reject(new Error("Web Audio could not start")), 1500);
        }),
      ]);
    } finally {
      clearTimeout(resumeTimeout);
    }
    await context.audioWorklet.addModule(new URL("./voiceProcessor.worklet.js", import.meta.url).href);
    const source = context.createMediaStreamSource(input);
    const highpass = context.createBiquadFilter();
    highpass.type = "highpass";
    highpass.frequency.value = 85;
    highpass.Q.value = 0.707;
    const lowpass = context.createBiquadFilter();
    lowpass.type = "lowpass";
    lowpass.frequency.value = 10000;
    lowpass.Q.value = 0.707;
    const compressor = context.createDynamicsCompressor();
    compressor.threshold.value = -18;
    compressor.knee.value = 12;
    compressor.ratio.value = 4;
    compressor.attack.value = 0.003;
    compressor.release.value = 0.15;
    const limiter = context.createDynamicsCompressor();
    limiter.threshold.value = -3;
    limiter.knee.value = 0;
    limiter.ratio.value = 20;
    limiter.attack.value = 0.001;
    limiter.release.value = 0.08;
    const gate = new AudioWorkletNode(context, "voice-noise-gate", {
      numberOfInputs: 1, numberOfOutputs: 1, outputChannelCount: [1],
      channelCount: 1, channelCountMode: "explicit",
    });
    const output = context.createMediaStreamDestination();
    source.connect(highpass).connect(lowpass).connect(gate).connect(compressor).connect(limiter).connect(output);
    return {
      stream: output.stream,
      dispose: () => {
        source.disconnect();
        gate.disconnect();
        output.stream.getTracks().forEach(track => track.stop());
        void context.close().catch(() => {});
      },
    };
  } catch (error) {
    void context.close().catch(() => {});
    throw error;
  }
}
