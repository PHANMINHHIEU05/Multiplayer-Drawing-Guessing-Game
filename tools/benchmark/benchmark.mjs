#!/usr/bin/env node
/**
 * TV9 — FINAL PERFORMANCE BENCHMARK DRIVER
 *
 * Runs the REAL authenticated stack (JWT session, bound identity, rate limits
 * ON) with synthetic Node WebSocket clients:
 *
 *   - Protocol comparison: JSON_POINT vs JSON_BATCH vs BINARY_BATCH (same
 *     semantic 60 pts/sec workload, only transport differs)
 *   - Load scaling: 4 / 10 / 20 (+ optional stress) players
 *   - Topology: 1 Gateway vs 2 Gateways (clients split ~50/50, rooms split
 *     across gateways so Redis Pub/Sub fanout is genuinely exercised)
 *   - Room distribution: 1 room vs N rooms × 4 players
 *   - Mixed workload: drawing + occasional guesses + low-frequency chat
 *
 * Metrics: app-level RTT (p50/p95/p99/jitter via APP_PING/APP_PONG), TX/RX
 * messages+bytes, points/batches/sec, bytes/point, sequence gaps, errors,
 * rate-limit hits; docker stats (CPU%/mem) sampled every 1s.
 *
 * Usage:
 *   node tools/benchmark/benchmark.mjs --protocol BINARY_BATCH --players 10 --rooms 1 --gateways 2 --runs 3
 *   node tools/benchmark/benchmark.mjs --matrix          # full 3×3×2 matrix
 *   node tools/benchmark/benchmark.mjs --stress          # increasing load until degradation
 *
 * Output: benchmark-results/raw/<ts>-<config>.json + summary CSV/JSON.
 */

import { Metrics, aggregate, toCsv } from './metrics.mjs';
import { generateWorkload, scheduleStrokes, POINTS_PER_SEC } from './workload.mjs';
import { execSync } from 'node:child_process';
import { mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';

// ─── CLI ─────────────────────────────────────────────────────────────
const args = process.argv.slice(2);
function arg(name, def) {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith('--') ? args[i + 1] : def;
}
const MODE_MATRIX = args.includes('--matrix');
const MODE_STRESS = args.includes('--stress');
const CFG = {
  protocol: arg('protocol', 'BINARY_BATCH'),
  players: parseInt(arg('players', '10'), 10),
  rooms: parseInt(arg('rooms', '1'), 10),
  gateways: parseInt(arg('gateways', '1'), 10),
  runs: parseInt(arg('runs', '3'), 10),
  warmupSec: parseInt(arg('warmup', '15'), 10),
  measureSec: parseInt(arg('measure', '30'), 10),
  mixed: args.includes('--mixed') || arg('mixed', '0') === '1',
  seed: parseInt(arg('seed', '1234'), 10),
  pointsPerSec: parseInt(arg('pointsPerSec', '60'), 10),
};

const GW1 = process.env.GW1_URL || 'ws://localhost:8080/ws';
const GW2 = process.env.GW2_URL || 'ws://localhost:8090/ws';
const RESULTS_DIR = 'benchmark-results';
mkdirSync(`${RESULTS_DIR}/raw`, { recursive: true });
mkdirSync(`${RESULTS_DIR}/summary`, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ─── Environment record ──────────────────────────────────────────────
function captureEnvironment() {
  const sh = (c) => { try { return execSync(c, { encoding: 'utf8' }).trim(); } catch { return 'n/a'; } };
  return {
    os: sh('uname -sr'),
    cpu: sh("lscpu | grep 'Model name' | head -1 | sed 's/Model name:\\s*//'"),
    cpuCores: sh('nproc'),
    ram: sh("free -h | awk '/Mem:/ {print $2}'"),
    java: sh('java -version 2>&1 | head -1'),
    node: process.version,
    docker: sh('docker --version'),
    topology: `${CFG.gateways} gateway(s)`,
    date: new Date().toISOString(),
  };
}

// ─── Docker stats sampler ─────────────────────────────────────────────
const CONTAINERS = {
  'gateway-1': 'drawgame-realtime-gateway',
  'gateway-2': 'drawgame-realtime-gateway-2',
  redis: 'drawgame-redis',
  'game-service': 'drawgame-game-service',
  'room-service': 'drawgame-room-service',
  'chat-service': 'drawgame-chat-service',
  postgres: 'drawgame-postgres',
};
// IMPORTANT: `docker stats --no-stream` is spawned ASYNCHRONOUSLY (never
// execSync) — a synchronous spawn blocks the Node event loop for ~1-2s and
// would contaminate every RTT measurement. Sampling every 5s is plenty for
// CPU%/mem trends and keeps overhead negligible.
import { exec } from 'node:child_process';
function sampleDockerStatsAsync() {
  return new Promise((resolve) => {
    exec(
      `docker stats --no-stream --format '{{.Name}}\\t{{.CPUPerc}}\\t{{.MemUsage}}' ${Object.values(CONTAINERS).join(' ')}`,
      { timeout: 15000 },
      (err, stdout) => {
        if (err) return resolve({});
        const out = {};
        for (const line of stdout.trim().split('\n')) {
          const [name, cpu, mem] = line.split('\t');
          // "238.1MiB / 15.33GiB" — parse the USED value (first match), and only
          // multiply by 1024 when the FIRST unit is GiB (checking the whole string
          // for 'GiB' would wrongly match the LIMIT part and inflate MiB values).
          const memMatch = /([\d.]+)([GM])iB/.exec(mem);
          const memUnit = memMatch ? memMatch[2] : 'M';
          out[name] = {
            cpuPercent: parseFloat(cpu.replace('%', '')) || 0,
            memMb: memMatch ? +(parseFloat(memMatch[1]) * (memUnit === 'G' ? 1024 : 1)).toFixed(1) : 0,
          };
        }
        resolve(out);
      }
    );
  });
}
function startResourceSampler() {
  const samples = [];
  let running = false;
  const iv = setInterval(async () => {
    if (running) return; // never overlap calls
    running = true;
    const stats = await sampleDockerStatsAsync();
    samples.push({ at: Date.now(), stats });
    running = false;
  }, 5000);
  return {
    stop: () => { clearInterval(iv); return samples; },
    samples: () => samples,
  };
}
function summarizeResources(samples, filterWarmupSec) {
  const cut = Date.now() - filterWarmupSec * 1000;
  const relevant = samples.filter((s) => s.at >= cut);
  const out = {};
  for (const key of Object.keys(CONTAINERS)) {
    const name = CONTAINERS[key];
    const cpus = relevant.map((s) => s.stats[name]?.cpuPercent).filter((v) => v != null);
    const mems = relevant.map((s) => s.stats[name]?.memMb).filter((v) => v != null);
    if (cpus.length) {
      out[`${key}_cpu_avg`] = +(cpus.reduce((a, b) => a + b, 0) / cpus.length).toFixed(1);
      out[`${key}_cpu_peak`] = Math.max(...cpus);
      out[`${key}_mem_mb`] = +((mems.reduce((a, b) => a + b, 0) / mems.length) || 0).toFixed(0);
    }
  }
  return out;
}

// ─── Authenticated benchmark client ──────────────────────────────────
class BenchClient {
  constructor(name, playerId, username, gatewayUrl) {
    this.name = name; this.playerId = playerId; this.username = username;
    this.gatewayUrl = gatewayUrl;
    this.ws = null; this.reqId = 0; this.pending = new Map();
    this.sessionToken = null;
    this.metrics = null; // set per-run
    this.isDrawer = false;
    this.strokeSeq = new Map(); // strokeId -> next expected seq
    this.pingInterval = null;
  }

  connect(metrics) {
    this.metrics = metrics;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`${this.name}: connect timeout`)), 8000);
      this.ws = new WebSocket(this.gatewayUrl);
      this.ws.binaryType = 'arraybuffer';
      this.ws.onopen = () => { clearTimeout(timer); resolve(); };
      this.ws.onerror = (e) => { clearTimeout(timer); reject(new Error(`${this.name}: ws error`)); };
      this.ws.onclose = () => { if (this.metrics) this.metrics.disconnects++; };
      this.ws.onmessage = (ev) => {
        if (ev.data instanceof ArrayBuffer) {
          if (this.metrics) this.metrics.recordRx(ev.data.byteLength);
          this.handleBinary(ev.data);
          return;
        }
        if (this.metrics) this.metrics.recordRx(ev.data.length);
        this.handleText(ev.data);
      };
    });
  }

  handleText(data) {
    let msg;
    try { msg = JSON.parse(data); } catch { return; }
    if (msg.sessionToken) this.sessionToken = msg.sessionToken;
    if (msg.type === 'APP_PONG' && this.metrics) {
      const t0 = msg.clientTimestamp;
      if (t0) this.metrics.recordRtt(Date.now() - t0);
      return;
    }
    if (msg.type === 'ERROR' && this.metrics) {
      if (msg.code === 'RATE_LIMITED') this.metrics.rateLimitHits++;
      else if (['INVALID_SESSION_TOKEN', 'AUTH_REQUIRED', 'SESSION_TOKEN_EXPIRED'].includes(msg.code)) this.metrics.authFailures++;
      else this.metrics.recordError('server_error', `${msg.code}: ${msg.message}`);
    }
    if (msg.requestId && this.pending.has(msg.requestId)) {
      const p = this.pending.get(msg.requestId);
      this.pending.delete(msg.requestId);
      clearTimeout(p.timer);
      if (msg.type === 'ERROR') p.reject(Object.assign(new Error(msg.code), { wsError: msg }));
      else p.resolve(msg);
    }
  }

  handleBinary(buf) {
    if (!this.metrics) return;
    const v = new DataView(buf);
    const opcode = v.getUint8(1);
    if (opcode === 0x01) {
      const b = new Uint8Array(buf);
      const hex = []; for (let i = 4; i < 20; i++) hex.push(b[i].toString(16).padStart(2, '0'));
      const strokeId = [hex.slice(0, 4).join(''), hex.slice(4, 6).join(''), hex.slice(6, 8).join(''), hex.slice(8, 10).join(''), hex.slice(10, 16).join('')].join('-');
      this.strokeSeq.set(strokeId, 0);
      this.metrics.batchesReceived++;
    } else if (opcode === 0x02) {
      const b = new Uint8Array(buf);
      const hex = []; for (let i = 10; i < 26; i++) hex.push(b[i].toString(16).padStart(2, '0'));
      const strokeId = [hex.slice(0, 4).join(''), hex.slice(4, 6).join(''), hex.slice(6, 8).join(''), hex.slice(8, 10).join(''), hex.slice(10, 16).join('')].join('-');
      const seqStart = v.getUint32(4, false);
      const count = v.getUint16(8, false);
      const expected = this.strokeSeq.get(strokeId);
      if (expected != null && seqStart > expected) this.metrics.sequenceGaps += (seqStart - expected);
      this.strokeSeq.set(strokeId, seqStart + count);
      this.metrics.batchesReceived++;
      this.metrics.pointsReceived += count;
    } else if (opcode === 0x04) {
      this.strokeSeq.clear();
      this.metrics.batchesReceived++;
    }
  }

  send(type, payload, timeout = 10000) {
    return new Promise((resolve, reject) => {
      if (!this.ws || this.ws.readyState !== 1) return reject(new Error('not connected'));
      const requestId = `bench-${this.name}-${++this.reqId}`;
      const raw = JSON.stringify({ type, requestId, payload });
      const timer = setTimeout(() => { this.pending.delete(requestId); reject(new Error(`timeout ${type}`)); }, timeout);
      this.pending.set(requestId, { resolve, reject, timer });
      if (this.metrics) this.metrics.recordTx(raw.length);
      this.ws.send(raw);
    });
  }

  sendRawText(text) {
    if (this.metrics) this.metrics.recordTx(text.length);
    this.ws.send(text);
  }

  sendBinary(buf) {
    if (this.metrics) { this.metrics.recordTx(buf.byteLength); this.metrics.drawingTxBytes = (this.metrics.drawingTxBytes || 0) + buf.byteLength; }
    this.ws.send(buf);
  }

  startPings(intervalMs = 1000) {
    this.stopPings();
    this.pingInterval = setInterval(() => {
      if (this.ws?.readyState !== 1) return;
      const raw = JSON.stringify({ type: 'APP_PING', timestamp: Date.now() });
      this.sendRawText(raw);
    }, intervalMs);
  }
  stopPings() { if (this.pingInterval) { clearInterval(this.pingInterval); this.pingInterval = null; } }

  close() { this.stopPings(); try { this.ws?.close(); } catch { /* ignore */ } }
}

// ─── Binary codec (production wire format) ───────────────────────────
const Q = 65535.0;
function uuidBytes(str) {
  const clean = str.replace(/-/g, '');
  const b = new Uint8Array(16);
  for (let i = 0; i < 16; i++) b[i] = parseInt(clean.substring(i * 2, i * 2 + 2), 16) || 0;
  return b;
}
function encDrawStart(round, strokeId, x, y, colorHex, width) {
  const buf = new ArrayBuffer(28);
  const v = new DataView(buf);
  const b = new Uint8Array(buf);
  v.setUint8(0, 1); v.setUint8(1, 0x01); v.setUint16(2, round, false);
  b.set(uuidBytes(strokeId), 4);
  v.setUint16(20, Math.round(x * Q), false); v.setUint16(22, Math.round(y * Q), false);
  const n = parseInt(colorHex.replace('#', ''), 16);
  v.setUint8(24, (n >> 16) & 255); v.setUint8(25, (n >> 8) & 255); v.setUint8(26, n & 255);
  v.setUint8(27, width);
  return buf;
}
function encDrawBatch(round, strokeId, seqStart, points) {
  const n = Math.min(points.length, 256);
  const buf = new ArrayBuffer(26 + n * 4);
  const v = new DataView(buf);
  const b = new Uint8Array(buf);
  v.setUint8(0, 1); v.setUint8(1, 0x02); v.setUint16(2, round, false);
  v.setUint32(4, seqStart, false); v.setUint16(8, n, false);
  b.set(uuidBytes(strokeId), 10);
  let off = 26;
  for (let i = 0; i < n; i++) {
    v.setUint16(off, Math.round(points[i].x * Q), false);
    v.setUint16(off + 2, Math.round(points[i].y * Q), false);
    off += 4;
  }
  return buf;
}

// ─── One benchmark run ───────────────────────────────────────────────
/**
 * Sets up real rooms/games, then draws with the given protocol for
 * (warmup + measure) seconds. Drawer(s) send; guessers receive + ping.
 * Only the measurement window is recorded.
 */
async function runOnce(cfg, runIndex) {
  const { protocol, players, rooms, gateways, warmupSec, measureSec, mixed, seed } = cfg;
  const t = () => `b${Date.now() % 100000}`;
  const perRoom = Math.max(2, Math.floor(players / rooms));

  // ─ Setup: hosts create rooms, players join (authenticated flow) ─
  const clients = [];
  const roomIds = [];
  const roomClients = [];

  for (let r = 0; r < rooms; r++) {
    const roomClientsList = [];
    for (let i = 0; i < perRoom; i++) {
      const idx = r * perRoom + i;
      if (idx >= players) break;
      const gwUrl = gateways === 2 ? (idx % 2 === 0 ? GW1 : GW2) : GW1;
      const c = new BenchClient(`p${idx}`, `bench${t()}_${idx}`, `Bench${idx}`, gwUrl);
      await c.connect(null);
      roomClientsList.push(c);
      clients.push(c);
    }
    const host = roomClientsList[0];
    const created = await host.send('CREATE_ROOM', {
      playerId: host.playerId, username: host.username, roomName: `Bench ${t()}`,
      maxPlayers: 10, totalRounds: 10, roundDuration: 180,
    });
    const roomId = created.roomId;
    roomIds.push(roomId);
    for (let i = 1; i < roomClientsList.length; i++) {
      await roomClientsList[i].send('JOIN_ROOM', { roomId, playerId: roomClientsList[i].playerId, username: roomClientsList[i].username });
    }
    await host.send('START_GAME', {});
    roomClients.push({ roomId, clients: roomClientsList });
    await sleep(150);
  }

  // Drawer = first client of each room (drawerId = playerOrder[0] = host)
  const metrics = new Metrics();
  for (const c of clients) {
    c.metrics = metrics; // start counting ALL traffic from now (setup excluded via reset below)
  }
  // bind metrics to receive path
  for (const c of clients) {
    const origHandler = c.ws.onmessage;
    c.ws.onmessage = (ev) => {
      if (ev.data instanceof ArrayBuffer) metrics.recordRx(ev.data.byteLength);
      else metrics.recordRx(ev.data.length);
      // reuse instance logic
      if (ev.data instanceof ArrayBuffer) c.handleBinary(ev.data); else c.handleText(ev.data);
    };
  }

  const drawers = [];
  const schedulers = [];
  const totalMs = (warmupSec + measureSec) * 1000;

  for (const room of roomClients) {
    const drawer = room.clients[0];
    drawer.isDrawer = true;
    drawers.push(drawer);
    const strokes = generateWorkload(totalMs, seed + roomIds.indexOf(room.roomId), cfg.pointsPerSec);
    const round = 1;

    let strokeCounter = 0;
    const workload = scheduleStrokes(strokes, (tick) => {
      if (protocol === 'JSON_POINT') {
        // one message per point (DRAW_POINT)
        const p = tick.p;
        const stroke = tick.stroke;
        if (!stroke._id) { stroke._id = crypto.randomUUID(); stroke._seq = 0; strokeCounter++; }
        const payload = {
          roomId: room.roomId, drawerId: drawer.playerId,
          point: { x: p.x, y: p.y, color: stroke.colorHex, size: stroke.width, isNewPath: stroke._seq === 0, strokeId: stroke._id },
        };
        const raw = JSON.stringify({ type: 'DRAW_POINT', payload });
        drawer.sendRawText(raw);
        metrics.pointsSent++;
        metrics.drawingTxBytes = (metrics.drawingTxBytes || 0) + raw.length;
        stroke._seq++;
      } else if (protocol === 'JSON_BATCH') {
        if (tick.kind !== 'batch') return;
        const stroke = tick.captured[0].stroke;
        if (!stroke._id) { stroke._id = crypto.randomUUID(); stroke._seq = 0; }
        const isNew = tick.captured.some((x) => x.p === stroke.points[0]);
        const points = tick.captured.map((x) => ({ x: x.p.x, y: x.p.y }));
        const seqStart = stroke._seq; stroke._seq += points.length;
        const payload = {
          roomId: room.roomId, drawerId: drawer.playerId,
          points: points.map((p) => ({ ...p, color: stroke.colorHex, size: stroke.width, isNewPath: false, strokeId: stroke._id })),
        };
        const raw = JSON.stringify({ type: isNew ? 'DRAW_POINT' : 'DRAW_BATCH', payload });
        // production uses DRAW_BATCH for multi-point; DRAW_POINT for single
        const type = points.length > 1 ? 'DRAW_BATCH' : 'DRAW_POINT';
        const raw2 = JSON.stringify({ type, payload });
        drawer.sendRawText(raw2);
        metrics.pointsSent += points.length;
        metrics.batchesSent++;
        metrics.drawingTxBytes = (metrics.drawingTxBytes || 0) + raw2.length;
      } else { // BINARY_BATCH
        if (tick.kind !== 'batch') return;
        const first = tick.captured[0];
        const stroke = first.stroke;
        if (!stroke._id) { stroke._id = crypto.randomUUID(); stroke._seq = 0; }
        const points = tick.captured.map((x) => ({ x: x.p.x, y: x.p.y }));
        let bytes = 0;
        if (first.p === stroke.points[0]) {
          const buf = encDrawStart(round, stroke._id, points[0].x, points[0].y, stroke.colorHex, stroke.width);
          drawer.sendBinary(buf); bytes += buf.byteLength;
          metrics.batchesSent++;
          const rest = points.slice(1);
          if (rest.length) {
            const seqStart = stroke._seq; stroke._seq += rest.length;
            const buf2 = encDrawBatch(round, stroke._id, seqStart, rest);
            drawer.sendBinary(buf2); bytes += buf2.byteLength;
            metrics.batchesSent++;
            metrics.pointsSent += rest.length;
          }
          metrics.pointsSent++;
        } else {
          const seqStart = stroke._seq; stroke._seq += points.length;
          const buf = encDrawBatch(round, stroke._id, seqStart, points);
          drawer.sendBinary(buf); bytes += buf.byteLength;
          metrics.batchesSent++;
          metrics.pointsSent += points.length;
        }
      }
    }, protocol === 'JSON_POINT' ? null : 16);
    schedulers.push(workload);
  }

  // Mixed workload: guessers guess occasionally + chat
  let mixedIv = null;
  if (mixed) {
    mixedIv = setInterval(() => {
      for (const room of roomClients) {
        for (const c of room.clients.slice(1)) {
          if (Math.random() < 0.3) c.send('SUBMIT_GUESS', { guess: `bench-probe-${Date.now() % 1000}` })
            .catch(async (e) => {
              const r = await c.send('GET_ROOM', {}).catch((e2) => e2?.wsError?.code || e2?.message);
              metrics.recordError('mixed_guess', `${c.name}(${c.gatewayUrl.includes('8090') ? 'GW2' : 'GW1'}): ${e.message || e.wsError?.code} | GET_ROOM=${typeof r === 'string' ? r : r?.type}`);
            });
          if (Math.random() < 0.2) c.send('SEND_CHAT', { content: 'benchmark chat' })
            .catch((e) => metrics.recordError('mixed_chat', `${c.name}(${c.gatewayUrl.includes('8090') ? 'GW2' : 'GW1'}): ${e.message || e.wsError?.code}`));
        }
      }
    }, 2000);
  }

  // app-level RTT pings from every client
  for (const c of clients) c.startPings(1000);

  // ─ Warmup → measure ─────────────────────────────────────────────
  const resSampler = startResourceSampler();
  await sleep(warmupSec * 1000);

  // reset everything that accumulated during warmup
  metrics.rttSamples = [];
  metrics.txMessages = 0; metrics.txBytes = 0;
  metrics.rxMessages = 0; metrics.rxBytes = 0;
  metrics.pointsSent = 0; metrics.batchesSent = 0;
  metrics.pointsReceived = 0; metrics.batchesReceived = 0;
  metrics.sequenceGaps = 0; metrics.errors = [];
  metrics.rateLimitHits = 0; metrics.authFailures = 0; metrics.disconnects = 0;
  metrics.drawingTxBytes = 0;
  const warmupEndedAt = Date.now();
  metrics.start();

  await sleep(measureSec * 1000);
  metrics.end();
  const allSamples = resSampler.stop();

  // ─ Teardown ──────────────────────────────────────────────────────
  schedulers.forEach((s) => s.stop());
  if (mixedIv) clearInterval(mixedIv);
  for (const c of clients) c.close();
  await sleep(500);

  const summary = metrics.summarize();
  const resources = summarizeResources(allSamples, measureSec + 2);
  const raw = {
    configuration: { ...cfg, run: runIndex, pointsPerSec: cfg.pointsPerSec },
    environment: ENV,
    timestamp: new Date().toISOString(),
    summary,
    // full error details for honest reporting (kind + sanitized detail)
    errors: metrics.errors,
    resources,
    warmupEndedAt,
  };
  const fname = `${RESULTS_DIR}/raw/${Date.now()}-${cfg.protocol}-p${cfg.players}-r${cfg.rooms}-g${cfg.gateways}-run${runIndex}.json`;
  writeFileSync(fname, JSON.stringify(raw, null, 2));

  return { summary, resources, file: fname, raw };
}

// ─── Matrix / stress orchestration ───────────────────────────────────
const ENV = captureEnvironment();

function configRow(cfg, run, summary, resources) {
  return {
    protocol: cfg.protocol, players: cfg.players, rooms: cfg.rooms,
    gateways: cfg.gateways, mixed: cfg.mixed ? 1 : 0, run,
    ...summary,
    ...resources,
  };
}

async function runConfigWithRepeats(cfg) {
  const runs = [];
  for (let i = 1; i <= cfg.runs; i++) {
    console.log(`\n▶ RUN ${i}/${cfg.runs}: ${cfg.protocol} | ${cfg.players} players | ${cfg.rooms} room(s) | ${cfg.gateways} GW | mixed=${cfg.mixed}`);
    const r = await runOnce(cfg, i);
    runs.push(r);
    const s = r.summary;
    console.log(`   RTT avg=${s.rtt_avg_ms}ms p50=${s.rtt_p50_ms} p95=${s.rtt_p95_ms} p99=${s.rtt_p99_ms} jitter=${s.jitter_ms}ms`);
    console.log(`   TX ${s.tx_msg_per_sec}/s ${s.tx_bytes_per_sec}B/s | RX ${s.rx_msg_per_sec}/s | bytes/point=${s.bytes_per_drawing_point?.toFixed(1)}`);
    console.log(`   gaps=${s.sequence_gaps} rateLimited=${s.rate_limit_hits} errors=${s.error_count} | gw1CPU=${r.resources['gateway-1_cpu_avg']}% redisCPU=${r.resources['redis_cpu_avg']}%`);
  }
  return runs;
}

async function main() {
  console.log('════════ TV9 PERFORMANCE BENCHMARK ════════');
  console.log(`Env: ${ENV.cpu} (${ENV.cpuCores} cores) | ${ENV.ram} RAM | Node ${ENV.node} | ${ENV.docker}`);

  const csvRows = [];
  const allAggregates = [];

  const configs = [];
  if (MODE_STRESS) {
    for (const players of [4, 10, 20, 30, 40]) {
      configs.push({ ...CFG, protocol: 'BINARY_BATCH', players, rooms: Math.max(1, Math.floor(players / 4)), gateways: 2, runs: 1, measureSec: 20 });
    }
  } else if (MODE_MATRIX) {
    // NOTE: room-service caps rooms at 10 players — the 20-player scenario uses
    // 2 rooms x 10 players (documented in the report; also how 20 real players
    // must be distributed on this architecture).
    for (const protocol of ['JSON_POINT', 'JSON_BATCH', 'BINARY_BATCH']) {
      for (const players of [4, 10, 20]) {
        for (const gateways of [1, 2]) {
          const rooms = players > 10 ? Math.ceil(players / 10) : 1;
          configs.push({ ...CFG, protocol, players, rooms, gateways, runs: CFG.runs });
        }
      }
    }
    // multi-room: 20 players / 5 rooms / 2 GW (BINARY)
    configs.push({ ...CFG, protocol: 'BINARY_BATCH', players: 20, rooms: 5, gateways: 2, runs: CFG.runs });
    // mixed workload: 10 players 1 room 2 GW (BINARY)
    configs.push({ ...CFG, protocol: 'BINARY_BATCH', players: 10, rooms: 1, gateways: 2, runs: CFG.runs, mixed: true });
  } else {
    configs.push(CFG);
  }

  for (const cfg of configs) {
    const runs = await runConfigWithRepeats(cfg);
    for (let i = 0; i < runs.length; i++) {
      csvRows.push(configRow(cfg, i + 1, runs[i].summary, runs[i].resources));
    }
    const agg = {
      configuration: { protocol: cfg.protocol, players: cfg.players, rooms: cfg.rooms, gateways: cfg.gateways, mixed: !!cfg.mixed },
      aggregate: aggregate(runs.map((r) => ({ ...r.summary, ...r.resources }))),
    };
    allAggregates.push(agg);
  }

  // ─── Outputs ──────────────────────────────────────────────────────
  const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const csvPath = `${RESULTS_DIR}/summary/benchmark-summary-${ts}.csv`;
  writeFileSync(csvPath, toCsv(csvRows));
  const summaryPath = `${RESULTS_DIR}/summary/benchmark-aggregates-${ts}.json`;
  writeFileSync(summaryPath, JSON.stringify({ environment: ENV, generatedAt: new Date().toISOString(), aggregates: allAggregates }, null, 2));

  console.log(`\n════════ DONE ════════`);
  console.log(`CSV: ${csvPath}`);
  console.log(`Aggregates: ${summaryPath}`);
  console.log(`Raw runs: benchmark-results/raw/ (${csvRows.length} runs)`);

  // quick verdict vs success criteria
  console.log('\n── Quick health check (medians) ──');
  for (const a of allAggregates) {
    const g = a.aggregate;
    console.log(`${a.configuration.protocol} p${a.configuration.players} g${a.configuration.gateways}${a.configuration.mixed ? ' mixed' : ''}: p95=${g.rtt_p95_ms?.median}ms gaps=${g.sequence_gaps?.median} errors=${g.error_count?.median} rateLimited=${g.rate_limit_hits?.median} gwCPU=${g['gateway-1_cpu_avg']?.median}%`);
  }
}

main().catch((e) => { console.error('FATAL:', e); process.exit(1); });
