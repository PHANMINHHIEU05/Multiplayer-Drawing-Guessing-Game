import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

describe("voice pipeline startup", () => {
  it("releases a suspended context so microphone startup can fall back", async () => {
    vi.useFakeTimers();
    const close = vi.fn().mockResolvedValue(undefined);
    const context = { resume: vi.fn(() => new Promise<void>(() => {})), close };
    const { createVoicePipeline } = await vi.importActual<typeof import("./voicePipeline")>("./voicePipeline");
    const result = createVoicePipeline({} as MediaStream, context as unknown as AudioContext)
      .catch(error => error as Error);
    await vi.advanceTimersByTimeAsync(1501);
    expect(await result).toBeInstanceOf(Error);
    expect(close).toHaveBeenCalledOnce();
  });
});
