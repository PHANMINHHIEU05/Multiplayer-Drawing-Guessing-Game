/**
 * TV9 — deterministic synthetic drawing workload.
 *
 * Semantically IDENTICAL path for all three protocols (JSON_POINT, JSON_BATCH,
 * BINARY_BATCH): x(t)/y(t) from a seeded PRNG so every mode draws the same
 * points in the same order; only the transport representation changes.
 *
 * Workload model (matches production frontend):
 *   - ~60 points/sec per active drawer (one sample every ~16ms)
 *   - stroke = 120 points (~2s), gap between strokes ~300ms
 *   - JSON_BATCH / BINARY_BATCH: flush every BATCH_WINDOW_MS (16ms) — mirrors
 *     the frontend usePointBatcher cadence
 */

/** Mulberry32 — small deterministic PRNG (seeded). */
export function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export const POINTS_PER_SEC = 60;
export const POINT_INTERVAL_MS = 1000 / POINTS_PER_SEC; // ~16.67ms
export const STROKE_POINTS = 120;                        // ~2s per stroke at 60 pts/s
export const STROKE_GAP_MS = 300;                        // pause between strokes
export const BATCH_WINDOW_MS = 16;                       // production batcher cadence

/**
 * Generate the full deterministic point stream for one drawer over `durationMs`.
 * Returns strokes: [{ points: [{x,y}], colorHex, width }]
 */
export function generateWorkload(durationMs, seed = 1234, pointsPerSec = POINTS_PER_SEC) {
  const pointIntervalMs = 1000 / pointsPerSec;
  const rand = mulberry32(seed);
  const strokes = [];
  let t = 0;
  while (t < durationMs) {
    const points = [];
    // deterministic-ish stroke: smooth random walk normalized 0..1
    let x = 0.15 + rand() * 0.6;
    let y = 0.15 + rand() * 0.6;
    const dx = (rand() - 0.5) * 0.012;
    const dy = (rand() - 0.5) * 0.012;
    for (let i = 0; i < STROKE_POINTS; i++) {
      x = Math.min(0.98, Math.max(0.02, x + dx + (rand() - 0.5) * 0.006));
      y = Math.min(0.98, Math.max(0.02, y + dy + (rand() - 0.5) * 0.006));
      points.push({ x: +x.toFixed(5), y: +y.toFixed(5), at: Math.round(t) });
      t += pointIntervalMs;
    }
    strokes.push({
      points,
      colorHex: '#EF4444',
      width: 8,
    });
    t += STROKE_GAP_MS;
  }
  return strokes;
}

/** Schedule a stroke stream with real-time pacing. Returns { stop, done }. */
export function scheduleStrokes(strokes, onTick, batchWindowMs = null) {
  let stopped = false;
  const timers = [];

  if (batchWindowMs == null) {
    // JSON_POINT: one message per point, paced at POINT_INTERVAL_MS
    for (const stroke of strokes) {
      for (const p of stroke.points) {
        timers.push(setTimeout(() => { if (!stopped) onTick({ kind: 'point', stroke, p }); }, p.at));
      }
    }
  } else {
    // BATCH modes: flush accumulated points every batchWindowMs
    const all = [];
    for (const stroke of strokes) {
      for (const p of stroke.points) all.push({ stroke, p });
    }
    let idx = 0;
    let elapsed = 0;
    while (idx < all.length) {
      const batch = [];
      const deadline = elapsed + batchWindowMs;
      while (idx < all.length && all[idx].p.at < deadline) {
        batch.push(all[idx]);
        idx++;
      }
      if (batch.length > 0) {
        const batchAt = elapsed;
        const captured = batch.slice();
        timers.push(setTimeout(() => { if (!stopped) onTick({ kind: 'batch', captured }); }, batchAt));
      }
      elapsed += batchWindowMs;
    }
  }

  const totalMs = strokes.length
    ? strokes[strokes.length - 1].points[strokes[strokes.length - 1].points.length - 1].at
    : 0;
  return {
    stop() { stopped = true; timers.forEach(clearTimeout); },
    done: new Promise((res) => setTimeout(res, totalMs + 500)),
  };
}
