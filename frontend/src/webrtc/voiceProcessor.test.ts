import { runInNewContext } from "node:vm";
import { describe, expect, it } from "vitest";
import processorSource from "./voiceProcessor.worklet.js?raw";

function createGate() {
  let Processor: any;
  runInNewContext(processorSource, {
    AudioWorkletProcessor: class {},
    sampleRate: 48000,
    registerProcessor: (_name: string, processor: any) => { Processor = processor; },
  });
  const gate = new Processor();
  return (amplitude: number, blocks = 1) => {
    const input = new Float32Array(128).fill(amplitude);
    const output = new Float32Array(128);
    for (let i = 0; i < blocks; i++) gate.process([[input]], [[output]]);
    return output;
  };
}

describe("audio-thread noise gate", () => {
  it("suppresses quiet background without amplifying it", () => {
    const process = createGate();
    expect(Math.max(...process(0.001, 400))).toBe(0);
  });

  it("opens smoothly for speech and holds short pauses", () => {
    const process = createGate();
    const attack = process(0.1);
    expect(attack[0]).toBeGreaterThan(0);
    expect(attack[0]).toBeLessThan(attack[127]);
    expect(process(0.1, 40)[127]).toBeCloseTo(0.1, 3);
    expect(process(0.001, 20)[127]).toBeCloseTo(0.001, 5);
  });

  it("closes after sustained silence and reopens for a quiet speaker", () => {
    const process = createGate();
    process(0.1, 40);
    expect(process(0.001, 400)[127]).toBeLessThan(0.00001);
    expect(process(0.008, 40)[127]).toBeCloseTo(0.008, 4);
  });
});
