/**
 * TV9 — benchmark metrics collection & statistics.
 *
 * RTT: application-level (APP_PING/APP_PONG echo through the real WebSocket
 * path — never ICMP). Percentiles: nearest-rank. Jitter: mean absolute
 * difference between consecutive RTT samples (same definition for every
 * experiment, mirroring the frontend metricsStore).
 */

export class Metrics {
  constructor() {
    this.rttSamples = [];
    this.txMessages = 0; this.txBytes = 0;
    this.rxMessages = 0; this.rxBytes = 0;
    this.pointsSent = 0; this.batchesSent = 0;
    this.pointsReceived = 0; this.batchesReceived = 0;
    this.sequenceGaps = 0;
    this.errors = [];
    this.rateLimitHits = 0;
    this.authFailures = 0;
    this.disconnects = 0;
    this.duplicateFrames = 0;
    this.startedAt = null; this.endedAt = null;
    // resource samples (docker stats)
    this.resources = [];
  }

  start() { this.startedAt = Date.now(); }
  end() { this.endedAt = Date.now(); }

  recordTx(bytes) { this.txMessages++; this.txBytes += bytes; }
  recordRx(bytes) { this.rxMessages++; this.rxBytes += bytes; }
  recordRtt(ms) { this.rttSamples.push(ms); }
  recordError(kind, detail) { this.errors.push({ kind, detail: String(detail).slice(0, 200), at: Date.now() - (this.startedAt || Date.now()) }); }

  durationSec() {
    if (!this.startedAt || !this.endedAt) return 0;
    return (this.endedAt - this.startedAt) / 1000;
  }

  /** Aggregate into the report row shape. */
  summarize() {
    const dur = this.durationSec() || 1;
    const rtts = this.rttSamples.slice().sort((a, b) => a - b);
    const pick = (q) => (rtts.length === 0 ? null : rtts[Math.min(rtts.length - 1, Math.round(q * (rtts.length - 1)))]);
    let jitter = null;
    if (rtts.length > 1) {
      let diffSum = 0;
      for (let i = 1; i < rtts.length; i++) diffSum += Math.abs(rtts[i] - rtts[i - 1]);
      jitter = +(diffSum / (rtts.length - 1)).toFixed(2);
    }
    const avg = rtts.length ? +(rtts.reduce((a, b) => a + b, 0) / rtts.length).toFixed(2) : null;
    return {
      rtt_avg_ms: avg,
      rtt_p50_ms: pick(0.5),
      rtt_p95_ms: pick(0.95),
      rtt_p99_ms: pick(0.99),
      rtt_min_ms: rtts.length ? rtts[0] : null,
      rtt_max_ms: rtts.length ? rtts[rtts.length - 1] : null,
      jitter_ms: jitter,
      rtt_samples: rtts.length,
      tx_msg_total: this.txMessages,
      rx_msg_total: this.rxMessages,
      tx_msg_per_sec: +(this.txMessages / dur).toFixed(1),
      rx_msg_per_sec: +(this.rxMessages / dur).toFixed(1),
      tx_bytes_total: this.txBytes,
      rx_bytes_total: this.rxBytes,
      tx_bytes_per_sec: +(this.txBytes / dur).toFixed(1),
      rx_bytes_per_sec: +(this.rxBytes / dur).toFixed(1),
      points_per_sec: +(this.pointsSent / dur).toFixed(1),
      batches_per_sec: +(this.batchesSent / dur).toFixed(1),
      points_per_batch: this.batchesSent > 0 ? +(this.pointsSent / this.batchesSent).toFixed(2) : null,
      drawing_bytes_total: this.drawingTxBytes || 0,
      bytes_per_drawing_point: (this.drawingTxBytes || 0) / Math.max(1, this.pointsSent),
      sequence_gaps: this.sequenceGaps,
      duplicate_frames: this.duplicateFrames,
      error_count: this.errors.length,
      rate_limit_hits: this.rateLimitHits,
      auth_failures: this.authFailures,
      disconnects: this.disconnects,
      duration_sec: +dur.toFixed(1),
    };
  }
}

export function percentile(sortedArr, q) {
  if (!sortedArr.length) return null;
  return sortedArr[Math.min(sortedArr.length - 1, Math.round(q * (sortedArr.length - 1)))];
}

/** Aggregate N run summaries → median/min/max per numeric key. */
export function aggregate(summaries) {
  const keys = Object.keys(summaries[0]).filter((k) => typeof summaries[0][k] === 'number');
  const out = { runs: summaries.length };
  for (const k of keys) {
    const vals = summaries.map((s) => s[k]).filter((v) => v != null).sort((a, b) => a - b);
    if (!vals.length) { out[k] = { median: null, min: null, max: null }; continue; }
    out[k] = {
      median: vals[Math.floor(vals.length / 2)],
      min: vals[0],
      max: vals[vals.length - 1],
    };
  }
  return out;
}

export function toCsv(rows, columns) {
  const cols = columns || Object.keys(rows[0]);
  const esc = (v) => (v == null ? '' : String(v).replace(/"/g, '""'));
  const lines = [cols.join(',')];
  for (const r of rows) lines.push(cols.map((c) => esc(r[c])).join(','));
  return lines.join('\n');
}
