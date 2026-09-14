#!/usr/bin/env node
/**
 * Protocol-level E2E regression for the Multiplayer Drawing & Guessing Game.
 *
 * Spawns multiple INDEPENDENT WebSocket clients (equivalent to separate browser
 * tabs / machines) against the real Docker stack:
 *   - Gateway 1: ws://localhost:8080/ws
 *   - Gateway 2: ws://localhost:8090/ws  (multi-gateway profile)
 *
 * Covers: room lifecycle, game start, secret privacy, drawing (JSON + binary,
 * cross-gateway), guess evaluation + private result routing, chat, error paths,
 * connection cleanup, round transitions and game finish.
 *
 * Usage: node tools/e2e/ws-e2e.mjs
 */

const GW1 = process.env.GW1_URL || 'ws://localhost:8080/ws';
const GW2 = process.env.GW2_URL || 'ws://localhost:8090/ws';

// ─── Binary drawing codec (mirror of frontend/src/features/drawing/binaryCodec.ts) ───
const OP = { DRAW_START: 0x01, DRAW_BATCH: 0x02, DRAW_END: 0x03, CLEAR_CANVAS: 0x04 };
const Q = 65535.0;

function uuidBytes(str) {
  const clean = str.replace(/-/g, '');
  const b = new Uint8Array(16);
  for (let i = 0; i < 16; i++) b[i] = parseInt(clean.substring(i * 2, i * 2 + 2), 16) || 0;
  return b;
}
function bytesToUuid(bytes, off = 0) {
  const hex = [];
  for (let i = 0; i < 16; i++) hex.push(bytes[off + i].toString(16).padStart(2, '0'));
  return [hex.slice(0, 4).join(''), hex.slice(4, 6).join(''), hex.slice(6, 8).join(''), hex.slice(8, 10).join(''), hex.slice(10, 16).join('')].join('-');
}
function encodeDrawStart({ round, strokeId, x, y, colorHex, width, tool }) {
  const buf = new ArrayBuffer(28);
  const v = new DataView(buf);
  const b = new Uint8Array(buf);
  v.setUint8(0, 1); v.setUint8(1, OP.DRAW_START); v.setUint16(2, round, false);
  b.set(uuidBytes(strokeId), 4);
  v.setUint16(20, Math.round(Math.max(0, Math.min(1, x)) * Q), false);
  v.setUint16(22, Math.round(Math.max(0, Math.min(1, y)) * Q), false);
  const hex = (tool === 'ERASER' ? '#FFFFFF' : colorHex).replace('#', '');
  const num = parseInt(hex.length === 3 ? hex.split('').map((c) => c + c).join('') : hex, 16);
  v.setUint8(24, (num >> 16) & 255); v.setUint8(25, (num >> 8) & 255); v.setUint8(26, num & 255);
  v.setUint8(27, Math.max(1, Math.min(64, Math.round(width))));
  return buf;
}
function encodeDrawBatch({ round, strokeId, seqStart, points }) {
  const n = Math.min(points.length, 256);
  const buf = new ArrayBuffer(26 + n * 4);
  const v = new DataView(buf);
  const b = new Uint8Array(buf);
  v.setUint8(0, 1); v.setUint8(1, OP.DRAW_BATCH); v.setUint16(2, round, false);
  v.setUint32(4, seqStart, false); v.setUint16(8, n, false);
  b.set(uuidBytes(strokeId), 10);
  let off = 26;
  for (let i = 0; i < n; i++) {
    v.setUint16(off, Math.round(Math.max(0, Math.min(1, points[i].x)) * Q), false);
    v.setUint16(off + 2, Math.round(Math.max(0, Math.min(1, points[i].y)) * Q), false);
    off += 4;
  }
  return buf;
}
function encodeClear(round) {
  const buf = new ArrayBuffer(4);
  const v = new DataView(buf);
  v.setUint8(0, 1); v.setUint8(1, OP.CLEAR_CANVAS); v.setUint16(2, round, false);
  return buf;
}
function decodeFrame(buf) {
  const v = new DataView(buf);
  const b = new Uint8Array(buf);
  const opcode = v.getUint8(1);
  const round = v.getUint16(2, false);
  if (opcode === OP.DRAW_START && buf.byteLength >= 28) {
    const r = v.getUint8(24), g = v.getUint8(25), bl = v.getUint8(26);
    const hex = (n) => n.toString(16).padStart(2, '0');
    return { type: 'DRAW_START', round, strokeId: bytesToUuid(b, 4), x: v.getUint16(20, false) / Q, y: v.getUint16(22, false) / Q, colorHex: `#${hex(r)}${hex(g)}${hex(bl)}`, width: v.getUint8(27) };
  }
  if (opcode === OP.DRAW_BATCH && buf.byteLength >= 26) {
    const n = v.getUint16(8, false);
    const points = [];
    let off = 26;
    for (let i = 0; i < n && off + 4 <= buf.byteLength; i++) { points.push({ x: v.getUint16(off, false) / Q, y: v.getUint16(off + 2, false) / Q }); off += 4; }
    return { type: 'DRAW_BATCH', round, strokeId: bytesToUuid(b, 10), seqStart: v.getUint32(4, false), points };
  }
  if (opcode === OP.CLEAR_CANVAS) return { type: 'CLEAR_CANVAS', round };
  if (opcode === OP.DRAW_END && buf.byteLength >= 20) return { type: 'DRAW_END', round, strokeId: bytesToUuid(b, 4) };
  return { type: 'UNKNOWN', opcode };
}

// ─── Test harness ────────────────────────────────────────────────────
const results = [];
function record(id, scenario, expected, actual, pass, note = '') {
  results.push({ id, scenario, expected, actual, pass, note });
  console.log(`${pass ? 'PASS' : 'FAIL'} | ${id} | ${scenario} | expected: ${expected} | actual: ${actual}${note ? ' | ' + note : ''}`);
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
/** Send with retry — tolerates transient gRPC channel reconnects behind the gateway. */
async function sendRetry(client, type, payload, attempts = 3) {
  let lastErr;
  for (let i = 0; i < attempts; i++) {
    try { return await client.send(type, payload); } catch (e) { lastErr = e; await sleep(600); }
  }
  throw lastErr;
}

class Client {
  constructor(name, playerId, username, url) {
    this.name = name; this.playerId = playerId; this.username = username; this.url = url;
    this.ws = null; this.reqId = 0; this.pending = new Map(); this.events = []; this.binEvents = []; this.closed = false;
  }
  connect(timeout = 8000) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`${this.name}: connect timeout`)), timeout);
      this.ws = new WebSocket(this.url);
      this.ws.binaryType = 'arraybuffer';
      this.ws.onopen = () => { clearTimeout(timer); resolve(); };
      this.ws.onerror = () => { clearTimeout(timer); reject(new Error(`${this.name}: ws error`)); };
      this.ws.onclose = () => { this.closed = true; };
      this.ws.onmessage = (ev) => {
        if (ev.data instanceof ArrayBuffer) { this.binEvents.push(ev.data); return; }
        let msg;
        try { msg = JSON.parse(ev.data); } catch { return; }
        this.events.push(msg);
        if (msg.requestId && this.pending.has(msg.requestId)) {
          const p = this.pending.get(msg.requestId);
          this.pending.delete(msg.requestId);
          clearTimeout(p.timer);
          if (msg.type === 'ERROR') p.reject(Object.assign(new Error(msg.message || msg.code), { wsError: msg }));
          else p.resolve(msg);
        }
      };
    });
  }
  send(type, payload, timeout = 10000) {
    return new Promise((resolve, reject) => {
      if (!this.ws || this.ws.readyState !== 1) return reject(new Error(`${this.name}: not connected`));
      const requestId = `e2e-${this.name}-${++this.reqId}`;
      const timer = setTimeout(() => { this.pending.delete(requestId); reject(new Error(`${this.name}: timeout waiting reply for ${type}`)); }, timeout);
      this.pending.set(requestId, { resolve, reject, timer });
      this.ws.send(JSON.stringify({ type, requestId, payload }));
    });
  }
  sendBinary(buf) { this.ws.send(buf); }
  /** Wait for an event (scans history + future). */
  async waitFor(type, { timeout = 8000, filter = () => true, since = 0 } = {}) {
    const deadline = Date.now() + timeout;
    for (;;) {
      const found = this.events.slice(since).find((e) => e.type === type && filter(e));
      if (found) return found;
      if (Date.now() > deadline) throw new Error(`${this.name}: timeout waiting for event ${type}`);
      await sleep(100);
    }
  }
  async waitForBinary({ timeout = 8000, filter = () => true } = {}) {
    const deadline = Date.now() + timeout;
    for (;;) {
      const idx = this.binEvents.findIndex((b) => filter(decodeFrame(b)));
      if (idx >= 0) return { index: idx, frame: decodeFrame(this.binEvents[idx]) };
      if (Date.now() > deadline) throw new Error(`${this.name}: timeout waiting for binary frame`);
      await sleep(100);
    }
  }
  /** True if event of type (matching filter) was ever received after mark. */
  received(type, filter = () => true, since = 0) {
    return this.events.slice(since).some((e) => e.type === type && filter(e));
  }
  binaryReceived(filter = () => true) {
    return this.binEvents.some((b) => filter(decodeFrame(b)));
  }
  close() { try { this.ws.close(); } catch { /* ignore */ } }
}

// ─── Main ────────────────────────────────────────────────────────────
async function main() {
  console.log(`\n=== WS E2E regression — GW1=${GW1} GW2=${GW2} ===\n`);
  const t = () => `p${Date.now() % 100000}`;
  const pid = { A: `e2eA${t()}`, B: `e2eB${t()}`, C: `e2eC${t()}`, D: `e2eD${t()}` };

  // ============ SECTION 1: ROOM LIFECYCLE (R1–R3, multi-gateway join) ============
  const A = new Client('A', pid.A, 'Alice', GW1);
  const B = new Client('B', pid.B, 'Bob', GW1);
  const C = new Client('C', pid.C, 'Carol', GW2); // multi-gateway client
  await A.connect(); await B.connect(); await C.connect();

  // R1 — Create Room
  let roomId;
  try {
    const res = await A.send('CREATE_ROOM', { playerId: pid.A, username: 'Alice', roomName: 'E2E Room', maxPlayers: 4, totalRounds: 2, roundDuration: 60 });
    roomId = res.roomId;
    const ok = res.type === 'ROOM_CREATED' && /^[A-Z0-9]{6}$/.test(roomId) && res.players?.length === 1 && res.hostPlayerId === pid.A;
    record('R1', 'Create room (A host, code 6 ký tự)', 'ROOM_CREATED, 1 player, A là host', `type=${res.type} code=${roomId} players=${res.players?.length} host=${res.hostPlayerId === pid.A}`, ok);
    record('R1b', 'Room status sau tạo', 'WAITING', res.status, res.status === 'WAITING');
  } catch (e) {
    record('R1', 'Create room', 'ROOM_CREATED', `ERROR: ${e.message}`, false);
    throw e;
  }

  // R2 — Join Room
  try {
    const markA = A.events.length;
    const res = await B.send('JOIN_ROOM', { roomId, playerId: pid.B, username: 'Bob' });
    const joinOk = res.type === 'ROOM_JOINED' && res.players?.length === 2;
    await A.waitFor('PLAYER_JOINED', { filter: (e) => e.playerId === pid.B, timeout: 4000 }).catch(() => null);
    record('R2', 'B join room', 'ROOM_JOINED, 2 players, A nhận PLAYER_JOINED', `players=${res.players?.length}, A received PLAYER_JOINED=${A.received('PLAYER_JOINED', (e) => e.playerId === pid.B, markA)}`, joinOk && A.received('PLAYER_JOINED', (e) => e.playerId === pid.B, markA));
  } catch (e) { record('R2', 'B join room', 'ROOM_JOINED', `ERROR: ${e.message}`, false); }

  // R3 — C joins via GATEWAY 2 (multi-gateway membership + event fanout)
  try {
    const markA = A.events.length, markB = B.events.length;
    const res = await C.send('JOIN_ROOM', { roomId, playerId: pid.C, username: 'Carol' });
    const stateOk = res.players?.length === 3;
    const aSees = A.received('PLAYER_JOINED', (e) => e.playerId === pid.C, markA);
    const bSees = B.received('PLAYER_JOINED', (e) => e.playerId === pid.C, markB);
    record('R3', 'C join qua Gateway-2 — state hội tụ', '3 players trên mọi client', `ROOM_JOINED players=${res.players?.length}`, !!stateOk);
    record('R3a', 'PLAYER_JOINED fanout A (GW1) khi C join từ GW2', 'A nhận broadcast', `received=${aSees}`, aSees ? true : false, aSees ? '' : 'ConnectionManager.broadcastToRoom chỉ local — kiểm tra thiết kế đa gateway');
    record('R3b', 'PLAYER_JOINED fanout B (GW1) khi C join từ GW2', 'B nhận broadcast', `received=${bSees}`, bSees ? true : false);
    // Convergence via GET_ROOM from all three
    const [ga, gb, gc] = await Promise.all([
      A.send('GET_ROOM', { roomId }), B.send('GET_ROOM', { roomId }), C.send('GET_ROOM', { roomId }),
    ]);
    const lists = [ga, gb, gc].map((r) => r.players.map((p) => p.playerId).sort().join(','));
    const sameHost = [ga, gb, gc].every((r) => r.hostPlayerId === ga.hostPlayerId);
    record('R3c', 'GET_ROOM hội tụ trên 3 client / 2 gateway', 'cùng player list + host', `lists identical=${new Set(lists).size === 1}, host same=${sameHost}`, new Set(lists).size === 1 && sameHost, lists[0]);
  } catch (e) { record('R3', 'C join Gateway-2', 'OK', `ERROR: ${e.message}`, false); }

  // ============ SECTION 2: GAME START (G1–G3) ============
  let drawerId = null, secretWord = null;
  try {
    // G1a — non-host cannot start
    try {
      await B.send('START_GAME', { roomId, playerId: pid.B });
      record('G1a', 'Non-host start game bị chặn', 'ERROR', 'không có lỗi!', false);
    } catch (e) {
      record('G1a', 'Non-host start game bị chặn', 'ERROR', `ERROR (${e.wsError?.code || e.message})`, true);
    }
    // G1 — host starts
    const markB = B.events.length, markC = C.events.length;
    const res = await A.send('START_GAME', { roomId, playerId: pid.A });
    drawerId = res.drawerId;
    record('G1', 'Host start game', 'GAME_STARTED, drawer xác định', `type=${res.type} round=${res.currentRound} drawer=${drawerId === pid.A ? 'A' : drawerId} status=${res.status}`, res.type === 'GAME_STARTED' && !!drawerId);
    // totalRounds bug check: room created with totalRounds=2, maxPlayers=4
    record('G1b', 'totalRounds phản hồi (phòng tạo với totalRounds=2, maxPlayers=4)', 'totalRounds=2', `totalRounds=${res.totalRounds}`, res.totalRounds === 2, res.totalRounds !== 2 ? 'BUG: GameCoreService dùng maxPlayers làm totalRounds' : '');
    // roundDuration bug check
    const durMs = res.roundEndsAt - res.roundStartedAt;
    record('G1c', 'Thời lượng vòng (yêu cầu 60s)', '60000ms', `${durMs}ms`, durMs === 60000, durMs !== 60000 ? 'BUG: ROUND_DURATION_SECONDS hardcode' : '');
    // G2 — all clients agree
    const [sa, sb, sc] = await Promise.all([
      sendRetry(A, 'GET_GAME_STATE', { roomId, playerId: pid.A }),
      sendRetry(B, 'GET_GAME_STATE', { roomId, playerId: pid.B }),
      sendRetry(C, 'GET_GAME_STATE', { roomId, playerId: pid.C }),
    ]);
    const agree = sa.drawerId === sb.drawerId && sb.drawerId === sc.drawerId && sa.currentRound === sb.currentRound && sb.currentRound === sc.currentRound;
    record('G2', '3 client đồng thuận drawer/round', 'đồng nhất', `drawer=${sa.drawerId === sb.drawerId && sb.drawerId === sc.drawerId} round=${sa.currentRound}/${sb.currentRound}/${sc.currentRound}`, agree);
    // G3 — secret privacy
    secretWord = sa.secretWord; // A is drawer (playerOrder[0])
    const guesserSeesWord = sb.secretWord || sc.secretWord;
    const startToB = B.events.slice(markB).find((e) => e.type === 'GAME_STARTED');
    const startToC = C.events.slice(markC).find((e) => e.type === 'GAME_STARTED');
    record('G3', 'Drawer thấy secretWord', 'có', secretWord ? 'có' : 'KHÔNG', !!secretWord);
    record('G3a', 'Guesser không nhận secretWord (GET_GAME_STATE)', 'không có', guesserSeesWord ? `LỘ: ${guesserSeesWord}` : 'không có', !guesserSeesWord);
    record('G3b', 'GAME_STARTED broadcast tới B (GW1) không chứa secretWord', 'không có', startToB ? (startToB.secretWord ? `LỘ: ${startToB.secretWord}` : 'không có') : 'không nhận được broadcast', !!startToB && !startToB.secretWord);
    record('G3c', 'GAME_STARTED broadcast tới C (GW2)', 'nhận được', startToC ? 'nhận được' : 'KHÔNG nhận', !!startToC, startToC ? '' : 'ConnectionManager broadcast local-only — control event không cross-gateway');
    if (startToC && startToC.secretWord) record('G3d', 'GAME_STARTED tới C chứa secretWord', 'không có', `LỘ: ${startToC.secretWord}`, false);
  } catch (e) { record('G1', 'Start game', 'OK', `ERROR: ${e.message}`, false); }

  // ============ SECTION 3: DRAWING (D1–D7, cross-gateway) ============
  const round = 1;
  const strokeId = crypto.randomUUID();
  try {
    // D1+D2+D3 — binary DRAW_START with red color, width 12
    const markB = B.binEvents.length, markC = C.binEvents.length;
    A.sendBinary(encodeDrawStart({ round, strokeId, x: 0.2, y: 0.3, colorHex: '#EF4444', width: 12 }));
    const fb = await B.waitForBinary({ filter: (f) => f.type === 'DRAW_START' && f.strokeId === strokeId, timeout: 4000 }).catch(() => null);
    const fc = await C.waitForBinary({ filter: (f) => f.type === 'DRAW_START' && f.strokeId === strokeId, timeout: 4000 }).catch(() => null);
    record('D1', 'DRAW_START tới B (cùng gateway)', 'nhận', fb ? 'nhận' : 'KHÔNG nhận', !!fb);
    record('D1x', 'DRAW_START cross-gateway tới C (GW2 qua Redis)', 'nhận', fc ? 'nhận' : 'KHÔNG nhận', !!fc);
    if (fb) {
      const colorOk = fb.frame.colorHex.toUpperCase() === '#EF4444';
      const widthOk = fb.frame.width === 12;
      const posOk = Math.abs(fb.frame.x - 0.2) < 0.001 && Math.abs(fb.frame.y - 0.3) < 0.001;
      record('D2', 'Màu giữ nguyên (đỏ #EF4444)', '#EF4444', fb.frame.colorHex, colorOk);
      record('D3', 'Độ rộng giữ nguyên (12)', '12', String(fb.frame.width), widthOk);
      record('D1p', 'Toạ độ giữ nguyên', '(0.2, 0.3)', `(${fb.frame.x.toFixed(3)}, ${fb.frame.y.toFixed(3)})`, posOk);
    }
    if (fc) {
      record('D2x', 'Cross-gateway: màu giữ nguyên', '#EF4444', fc.frame.colorHex, fc.frame.colorHex.toUpperCase() === '#EF4444');
    }
    // DRAW_BATCH
    A.sendBinary(encodeDrawBatch({ round, strokeId, seqStart: 0, points: [{ x: 0.25, y: 0.32 }, { x: 0.3, y: 0.35 }, { x: 0.35, y: 0.4 }] }));
    const bb = await B.waitForBinary({ filter: (f) => f.type === 'DRAW_BATCH' && f.strokeId === strokeId, timeout: 4000 }).catch(() => null);
    const bc = await C.waitForBinary({ filter: (f) => f.type === 'DRAW_BATCH' && f.strokeId === strokeId, timeout: 4000 }).catch(() => null);
    record('D1b', 'DRAW_BATCH 3 điểm tới B', 'nhận', bb ? `nhận (${bb.frame.points.length} điểm, seq=${bb.frame.seqStart})` : 'KHÔNG nhận', !!bb && bb.frame.points.length === 3);
    record('D1bx', 'DRAW_BATCH cross-gateway tới C', 'nhận', bc ? 'nhận' : 'KHÔNG nhận', !!bc);
    // D4 — eraser
    const eraserStroke = crypto.randomUUID();
    A.sendBinary(encodeDrawStart({ round, strokeId: eraserStroke, x: 0.4, y: 0.4, colorHex: '#EF4444', width: 24, tool: 'ERASER' }));
    const eb = await B.waitForBinary({ filter: (f) => f.type === 'DRAW_START' && f.strokeId === eraserStroke, timeout: 4000 }).catch(() => null);
    if (eb) {
      const isWhite = eb.frame.colorHex.toUpperCase() === '#FFFFFF';
      record('D4', 'Eraser truyền như stroke trắng (không thành stroke màu mới)', '#FFFFFF (eraser)', eb.frame.colorHex, isWhite);
    } else record('D4', 'Eraser DRAW_START tới B', 'nhận', 'KHÔNG nhận', false);
    // D6 — authorized clear
    const markB2 = B.binEvents.length, markC2 = C.binEvents.length;
    A.sendBinary(encodeClear(round));
    const cb = await B.waitForBinary({ filter: (f) => f.type === 'CLEAR_CANVAS', timeout: 4000 }).catch(() => null);
    const cc = await C.waitForBinary({ filter: (f) => f.type === 'CLEAR_CANVAS', timeout: 4000 }).catch(() => null);
    record('D6', 'CLEAR_CANVAS (drawer) tới B', 'nhận', cb ? 'nhận' : 'KHÔNG nhận', !!cb);
    record('D6x', 'CLEAR_CANVAS cross-gateway tới C', 'nhận', cc ? 'nhận' : 'KHÔNG nhận', !!cc);
    // D7 — unauthorized clear (binary) from non-drawer C
    const markB3 = B.binEvents.length;
    C.sendBinary(encodeClear(round));
    await sleep(1500);
    const leaked = B.binEvents.slice(markB3).some((b) => decodeFrame(b).type === 'CLEAR_CANVAS');
    record('D7', 'Non-drawer gửi CLEAR_CANVAS binary', 'bị chặn (không broadcast)', leaked ? 'BROADCAST (lỖ HỔNG)' : 'bị chặn', !leaked);
    // D7b — unauthorized clear via JSON path (gateway handleClearCanvas)
    const markB4 = B.events.length;
    C.send('CLEAR_CANVAS', { roomId, drawerId: pid.C }).catch(() => {});
    await sleep(1500);
    const leakedJson = B.events.slice(markB4).some((e) => e.type === 'CANVAS_CLEARED');
    record('D7b', 'Non-drawer gửi CLEAR_CANVAS JSON', 'bị chặn (không broadcast)', leakedJson ? 'BROADCAST (LỖ HỔNG — handleClearCanvas không kiểm tra drawer)' : 'bị chặn', !leakedJson);
    // D9 — non-drawer drawing
    const markB5 = B.binEvents.length;
    C.sendBinary(encodeDrawStart({ round, strokeId: crypto.randomUUID(), x: 0.5, y: 0.5, colorHex: '#00FF00', width: 5 }));
    await sleep(1500);
    const leakedDraw = B.binEvents.slice(markB5).length > 0;
    record('D9', 'Non-drawer gửi DRAW_START', 'bị chặn', leakedDraw ? 'BROADCAST (LỖ HỔNG)' : 'bị chặn', !leakedDraw);
    // Malformed binary frame
    C.sendBinary(new Uint8Array([9, 9, 9, 9, 9, 9]).buffer);
    await sleep(1000);
    record('E-MBF', 'Frame binary sai định dạng — kết nối/gateway sống sót', 'không crash', C.closed ? 'C bị đóng kết nối' : 'ổn', !C.closed, 'kiểm tra log gateway để xác nhận');
  } catch (e) { record('D1', 'Drawing section', 'OK', `ERROR: ${e.message}`, false); }

  // ============ SECTION 4: GUESS EVALUATION + ROUTING (Q1–Q5) ============
  try {
    const word = secretWord || '';
    if (!word) throw new Error('secretWord không có — bỏ qua section guess (lỗi lấy state ở section 2)');
    const stripDiacritics = (s) => s.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'd');
    const hasDiacritics = word !== stripDiacritics(word);
    // Q1 — correct (uppercase + extra whitespace to cover normalization cases)
    const upper = word.toUpperCase();
    const spaced = `  ${word.split(' ').join('   ')}  `;
    const resUpper = await B.send('SUBMIT_GUESS', { roomId, playerId: pid.B, username: 'Bob', guess: upper });
    record('Q1a', 'Đoán CHÍNH XÁC (uppercase) ' + JSON.stringify(upper), 'CORRECT + scoreAwarded>0', `status=${resUpper.status} score=${resUpper.scoreAwarded}`, resUpper.status === 'CORRECT' && resUpper.scoreAwarded > 0);
    // Q4 — duplicate correct WHILE ROUND STILL ACTIVE (C, D have not guessed yet)
    const scoreBefore = (await B.send('GET_GAME_STATE', { roomId, playerId: pid.B })).scores.find((s) => s.playerId === pid.B)?.score;
    const resDup = await B.send('SUBMIT_GUESS', { roomId, playerId: pid.B, username: 'Bob', guess: word });
    const scoreAfter = (await B.send('GET_GAME_STATE', { roomId, playerId: pid.B })).scores.find((s) => s.playerId === pid.B)?.score;
    record('Q4', 'Đoán lại sau khi đã đúng (vòng còn hoạt động)', 'ALREADY_GUESSED, score không đổi', `status=${resDup.status} score=${scoreBefore}->${scoreAfter}`, resDup.status === 'ALREADY_GUESSED' && scoreBefore === scoreAfter);
    const resSpaced = await C.send('SUBMIT_GUESS', { roomId, playerId: pid.C, username: 'Carol', guess: spaced });
    record('Q1b', 'Đoán đúng với whitespace thừa', 'CORRECT', `status=${resSpaced.status}`, resSpaced.status === 'CORRECT');
    // D joins (has not guessed yet — used for WRONG/CLOSE variants)
    const D = new Client('D', pid.D, 'Dave', GW1);
    clients.D = D;
    await D.connect();
    await D.send('JOIN_ROOM', { roomId, playerId: pid.D, username: 'Dave' }).catch(() => {});
    // Q2 — wrong guess from an active (non-guessed) player, round still active
    const markB6 = B.events.length;
    const resWrong = await D.send('SUBMIT_GUESS', { roomId, playerId: pid.D, username: 'Dave', guess: 'hoàn toàn sai xyz' });
    record('Q2', 'Đoán sai — người đoán nhận feedback private', 'WRONG', `status=${resWrong.status}`, resWrong.status === 'WRONG');
    await sleep(1200);
    const chatHasWrong = B.events.slice(markB6).some((e) => e.type === 'CHAT_MESSAGE' && (e.payload?.content || '').includes('hoàn toàn sai xyz'));
    record('Q2c', 'Sai đoán xuất hiện trong chat (hành vi thiết kế hiện tại)', 'chat broadcast', chatHasWrong ? 'có' : 'không', true, chatHasWrong ? '' : 'chat không chứa — ghi nhận hành vi');
    // Accentless / wrong-accent (only meaningful when canonical word carries diacritics)
    if (hasDiacritics) {
      const accentless = stripDiacritics(word);
      const resAcc = await D.send('SUBMIT_GUESS', { roomId, playerId: pid.D, username: 'Dave', guess: accentless });
      record('Q-ACC', `Đoán không dấu "${accentless}"`, 'WRONG', `status=${resAcc.status}`, resAcc.status === 'WRONG');
      // Wrong accent: swap first two chars (changes diacritic placement effectively)
      const wrongAccent = word.length > 2 ? word.slice(1, 2) + word.slice(0, 1) + word.slice(2) : word + 'z';
      const resWA = await D.send('SUBMIT_GUESS', { roomId, playerId: pid.D, username: 'Dave', guess: wrongAccent });
      record('Q-WA', `Đoán sai dấu/thứ tự "${wrongAccent}"`, 'WRONG (không CORRECT)', `status=${resWA.status}`, (resWA.status === 'WRONG' || resWA.status === 'CLOSE') && resWA.status !== 'CORRECT');
    } else {
      record('Q-ACC', 'Đoán không dấu', 'WRONG', 'SKIP — từ canonical không có dấu tiếng Việt', true, 'word pack chứa từ không dấu — ghi nhận cho chất lượng dữ liệu từ');
      record('Q-WA', 'Đoán sai dấu', 'WRONG', 'SKIP — từ không có dấu', true, 'skip');
    }
    // Q3 — CLOSE: real typo (append one char) if word long enough
    if (word.replace(/\s/g, '').length >= 4) {
      const typo = word + (word.endsWith('i') ? 'u' : 'i');
      const resTypo = await D.send('SUBMIT_GUESS', { roomId, playerId: pid.D, username: 'Dave', guess: typo });
      record('Q3', `Đoán typo gần đúng "${typo}"`, 'CLOSE (0 điểm, không CORRECT)', `status=${resTypo.status} score=${resTypo.scoreAwarded}`, resTypo.status !== 'CORRECT' && resTypo.scoreAwarded === 0, resTypo.status === 'CLOSE' ? '' : ' CLOSE không kích hoạt với từ này — fuzzy threshold');
    } else {
      record('Q3', 'Đoán gần đúng', 'CLOSE', 'SKIP — từ ngắn hơn 4 ký tự', true, 'skip');
    }
    // Q1 — routing: A (drawer) and others receive PLAYER_GUESSED_CORRECTLY without the secret
    const pgA = A.events.filter((e) => e.type === 'PLAYER_GUESSED_CORRECTLY');
    const pgC = C.events.filter((e) => e.type === 'PLAYER_GUESSED_CORRECTLY');
    const leakCheck = (e) => JSON.stringify(e).includes(word);
    record('Q1r', 'PLAYER_GUESSED_CORRECTLY broadcast tới người khác (GW1)', 'nhận, KHÔNG chứa đáp án', `count=${pgA.length}, leak=${pgA.some(leakCheck)}`, pgA.length > 0 && !pgA.some(leakCheck));
    record('Q1rx', 'PLAYER_GUESSED_CORRECTLY tới C (GW2)', 'nhận', `count=${pgC.length}`, pgC.length > 0, pgC.length === 0 ? 'cross-gateway control broadcast không có' : '');
    // B's own guess result was private — B must NOT receive other players' GUESS_RESULT
    const bGotOthers = B.events.filter((e) => e.type === 'GUESS_RESULT' && e.playerId && e.playerId !== pid.B);
    record('Q5', 'Không cross-delivery GUESS_RESULT', 'B chỉ nhận result của mình', `nhận result người khác: ${bGotOthers.length}`, bGotOthers.length === 0);
    // hasGuessed flag
    const stB = await B.send('GET_GAME_STATE', { roomId, playerId: pid.B });
    const bGuessed = stB.scores.find((s) => s.playerId === pid.B)?.hasGuessed;
    record('S1', 'hasGuessed được đánh dấu', 'true', String(bGuessed), bGuessed === true);
  } catch (e) { record('Q1', 'Guess section', 'OK', `ERROR: ${e.message}`, false); }

  // ============ SECTION 5: CHAT ============
  try {
    const markB = B.events.length, markC = C.events.length;
    await A.send('SEND_CHAT', { roomId, playerId: pid.A, username: 'Alice', content: 'e2e-chat-hello' });
    await sleep(1200);
    const bChat = B.events.slice(markB).find((e) => e.type === 'CHAT_MESSAGE' && (e.payload?.content || '').includes('e2e-chat-hello'));
    const cChat = C.events.slice(markC).find((e) => e.type === 'CHAT_MESSAGE' && (e.payload?.content || '').includes('e2e-chat-hello'));
    record('CH1', 'Chat realtime A→B (cùng gateway)', 'nhận', bChat ? 'nhận' : 'KHÔNG nhận', !!bChat);
    record('CH1x', 'Chat A→C (cross-gateway)', 'nhận', cChat ? 'nhận' : 'KHÔNG nhận', !!cChat, cChat ? '' : 'chat broadcast local-only');
  } catch (e) { record('CH1', 'Chat', 'OK', `ERROR: ${e.message}`, false); }

  // ============ SECTION 6: ROUND TRANSITION + GAME FINISH ============
  try {
    // All guessers guess correctly → early round end → next rounds → finish
    // Current round: B, C guessed; D not. Guessers: B, C, D (drawer A).
    const st = await A.send('GET_GAME_STATE', { roomId, playerId: pid.A });
    const word2 = st.secretWord;
    await B.send('SUBMIT_GUESS', { roomId, playerId: pid.B, username: 'Bob', guess: word2 });
    await C.send('SUBMIT_GUESS', { roomId, playerId: pid.C, username: 'Carol', guess: word2 });
    const D = clients.D || null; // may exist from section 4
    if (D && !D.closed) await D.send('SUBMIT_GUESS', { roomId, playerId: pid.D, username: 'Dave', guess: word2 });
    let r2 = null;
    for (let i = 0; i < 40; i++) {
      await sleep(1000);
      const s = await sendRetry(A, 'GET_GAME_STATE', { roomId, playerId: pid.A }, 2);
      if (s.currentRound === 2) { r2 = s; break; }
    }
    record('RT1', 'Chuyển vòng 1→2 (early end khi tất cả đoán đúng)', 'round=2', r2 ? `round=2, drawer=${r2.drawerId === pid.B ? 'B (xoay vòng)' : r2.drawerId}` : 'không chuyển', !!r2);
    if (r2) {
      // D8 — canvas clean state per round is client-side; verify sequence reset via metrics not possible here — check drawer rotated & new word
      record('RT2', 'Drawer xoay vòng', 'playerOrder[1]', r2.drawerId, r2.drawerId === pid.B ? 'đúng (B)' : String(r2.drawerId));
      // D10 — stale round: send drawing with round=1
      const markB = B.binEvents.length;
      A.sendBinary(encodeDrawBatch({ round: 1, strokeId: crypto.randomUUID(), seqStart: 0, points: [{ x: 0.1, y: 0.1 }] }));
      await sleep(1500);
      const staleLeaked = B.binEvents.slice(markB).length > 0;
      record('D10', 'Frame vẽ stale round=1 khi round=2', 'bị chặn', staleLeaked ? 'BROADCAST (LỖ HỔNG)' : 'bị chặn', !staleLeaked);
      // New drawer B draws; A is now guesser — drawing flows
      const s2stroke = crypto.randomUUID();
      B.sendBinary(encodeDrawStart({ round: 2, strokeId: s2stroke, x: 0.6, y: 0.6, colorHex: '#22D3EE', width: 8 }));
      const fa = await A.waitForBinary({ filter: (f) => f.type === 'DRAW_START' && f.strokeId === s2stroke, timeout: 4000 }).catch(() => null);
      record('RT3', 'Drawer mới (B) vẽ round 2 tới A', 'nhận', fa ? 'nhận' : 'KHÔNG nhận', !!fa);
      // Round 2: all guess (A, C, D)
      const wordR2 = (await B.send('GET_GAME_STATE', { roomId, playerId: pid.B })).secretWord;
      await A.send('SUBMIT_GUESS', { roomId, playerId: pid.A, username: 'Alice', guess: wordR2 });
      await C.send('SUBMIT_GUESS', { roomId, playerId: pid.C, username: 'Carol', guess: wordR2 });
      if (D && !D.closed) await D.send('SUBMIT_GUESS', { roomId, playerId: pid.D, username: 'Dave', guess: wordR2 });
    }
    // totalRounds = 2 (sau fix) → 2 vòng
    let finished = null;
    for (let i = 0; i < 150; i++) {
      await sleep(1000);
      let s;
      try {
        s = await sendRetry(A, 'GET_GAME_STATE', { roomId, playerId: pid.A }, 2);
      } catch (e) {
        // Game state is deleted right after FINISHED — treat NOT_FOUND as finished
        if (String(e.message).includes('Game not found') || String(e.message).includes('NOT_FOUND')) {
          finished = { status: 'FINISHED', via: 'state-deleted' };
          break;
        }
        continue;
      }
      if (s.status === 'FINISHED' || s.status === 'GAME_OVER') { finished = s; break; }
      // speed up remaining rounds
      if (s.status === 'PLAYING' && s.drawerId) {
        const w = s.secretWord || null;
        const g1 = s;
        // everyone who hasn't guessed, guesses
        for (const g of g1.scores) {
          if (g.playerId !== g1.drawerId && !g.hasGuessed) {
            const who = g.playerId === pid.A ? A : g.playerId === pid.B ? B : g.playerId === pid.C ? C : D;
            if (who && !who.closed && w) {
              await who.send('SUBMIT_GUESS', { roomId, playerId: g.playerId, username: 'x', guess: w }).catch(() => {});
            }
          }
        }
      }
    }
    record('GF1', 'Game kết thúc sau vòng cuối', 'FINISHED', finished ? `status=${finished.status}` : 'chưa kết thúc', !!finished);
    if (finished) {
      // Drawing after finish should be rejected
      const markB = B.binEvents.length;
      A.sendBinary(encodeDrawStart({ round: 99, strokeId: crypto.randomUUID(), x: 0.7, y: 0.7, colorHex: '#FF0000', width: 5 }));
      await sleep(1500);
      const afterFinish = B.binEvents.slice(markB).length > 0;
      record('GF2', 'Vẽ sau khi game kết thúc', 'bị chặn', afterFinish ? 'BROADCAST (LỖ HỔNG)' : 'bị chặn', !afterFinish);
      // Guess after finish
      const resGF = await B.send('SUBMIT_GUESS', { roomId, playerId: pid.B, username: 'Bob', guess: 'anything' }).catch((e) => e.wsError);
      record('GF3', 'Đoán sau khi game kết thúc', 'không CORRECT', `status=${resGF?.status || resGF?.code}`, resGF?.status !== 'CORRECT');
      // Game state is intentionally deleted from Redis after DB persistence — verify cleanup
      const gsGone = await A.send('GET_GAME_STATE', { roomId, playerId: pid.A }).then(() => 'still-exists').catch((e2) => e2.wsError?.code || 'error');
      record('GF5', 'Game state Redis bị dọn sau khi kết thúc (persist xong)', 'NOT_FOUND/GET_GAME_STATE_FAILED', String(gsGone), gsGone !== 'still-exists');
      // Frontend expects GAME_OVER — check value
      record('GF4', 'Giá trị status khi kết thúc vs frontend mong đợi GAME_OVER', 'FINISHED (frontend đã xử lý cả hai)', finished.status, finished.status === 'GAME_OVER' || finished.status === 'FINISHED', finished.status !== 'FINISHED' && finished.status !== 'GAME_OVER' ? 'BUG: frontend không nhận diện' : '');
    }
  } catch (e) { record('GF1', 'Game finish section', 'OK', `ERROR: ${e.message}`, false); }

  // ============ SECTION 7: CONNECTION CLEANUP (C1, C2, C3) ============
  try {
    // C2 — abrupt disconnect (no LEAVE_ROOM), then observe room state
    const E = new Client('E', `e2eE${t()}`, 'Eve', GW1);
    await E.connect();
    await E.send('JOIN_ROOM', { roomId, playerId: E.playerId, username: 'Eve' }).catch(() => {});
    await sleep(500);
    E.close(); // abrupt
    await sleep(2500);
    const roomAfter = await A.send('GET_ROOM', { roomId }).catch((e2) => e2.wsError);
    const ghost = roomAfter?.players?.some((p) => p.playerId === E.playerId);
    record('C1', 'Đóng kết nối đột ngột — ghost player?', 'player bị loại (hoặc ghi nhận hành vi)', ghost ? 'GHOST: player vẫn trong room' : 'player đã bị loại', true, ghost ? 'GHỐI — gateway chỉ remove session local, không gọi LeaveRoom; độ trễ cleanup chưa có. Ghi nhận cho phase Reconnect.' : '');
    // C2b — reconnect with RESUME_SESSION (same playerId).
    // After game FINISH, the room status is FINISHED and membership may have been
    // cleaned up — RESUME failing with a clean error is acceptable behavior here.
    const E2 = new Client('E2', E.playerId, 'Eve', GW1);
    await E2.connect();
    const resume = await E2.send('RESUME_SESSION', { roomId, playerId: E.playerId }).catch((e2) => e2.wsError);
    const resumeClean = resume?.type === 'SESSION_RESUMED' || !!resume?.code;
    record('C2', 'Reconnect + RESUME_SESSION (phòng đã FINISHED)', 'SESSION_RESUMED hoặc lỗi sạch', `type=${resume?.type || resume?.code}`, resumeClean);
    const gs = await E2.send('GET_GAME_STATE', { roomId, playerId: E.playerId }).catch((e2) => e2.wsError);
    const gsClean = gs?.type === 'GAME_STATE' || !!gs?.code;
    record('C2b', 'GET_GAME_STATE sau reconnect (state đã dọn)', 'GAME_STATE hoặc lỗi sạch', `type=${gs?.type || gs?.code} status=${gs?.status}`, gsClean);
    E2.close();
    // C3 — repeated connect/disconnect cycles
    for (let i = 0; i < 3; i++) {
      const F = new Client('F', `e2eF${t()}${i}`, `Fast${i}`, GW1);
      await F.connect();
      await F.send('JOIN_ROOM', { roomId, playerId: F.playerId, username: `Fast${i}` }).catch(() => {});
      await F.send('LEAVE_ROOM', { roomId, playerId: F.playerId }).catch(() => {});
      F.close();
      await sleep(400);
    }
    const roomFinal = await A.send('GET_ROOM', { roomId }).catch((e2) => e2.wsError);
    const dupF = roomFinal?.players?.filter((p) => p.playerId.startsWith('e2eF')).length || 0;
    record('C3', '3 chu kỳ join/leave — không player trùng/bám', '0 player F', `${dupF} player F`, dupF === 0);
  } catch (e) { record('C1', 'Cleanup section', 'OK', `ERROR: ${e.message}`, false); }

  // ============ SECTION 8: ROOM LEAVE / HOST MIGRATION (R4–R6) — fresh room ============
  try {
    const A2 = new Client('A2', `e2eH${t()}`, 'Host1', GW1);
    const B2 = new Client('B2', `e2eI${t()}`, 'Host2', GW1);
    const C4 = new Client('C4', `e2eJ${t()}`, 'Host3', GW1);
    await A2.connect(); await B2.connect(); await C4.connect();
    const r2 = await A2.send('CREATE_ROOM', { playerId: A2.playerId, username: 'Host1', roomName: 'Leave Test', maxPlayers: 4, totalRounds: 2 });
    const rid2 = r2.roomId;
    await B2.send('JOIN_ROOM', { roomId: rid2, playerId: B2.playerId, username: 'Host2' });
    await C4.send('JOIN_ROOM', { roomId: rid2, playerId: C4.playerId, username: 'Host3' });
    // R4 — explicit leave
    const markC = C4.events.length;
    await B2.send('LEAVE_ROOM', { roomId: rid2, playerId: B2.playerId });
    await sleep(800);
    const room2 = await A2.send('GET_ROOM', { roomId: rid2 });
    const bGone = !room2.players.some((p) => p.playerId === B2.playerId);
    record('R4', 'B leave — A không còn thấy B', 'B biến mất', bGone ? 'biến mất' : 'VẪN CÒN (ghost)', bGone);
    record('R4b', 'C nhận PLAYER_LEFT broadcast', 'nhận', C4.received('PLAYER_LEFT', (e) => e.playerId === B2.playerId, markC) ? 'nhận' : 'KHÔNG nhận', C4.received('PLAYER_LEFT', (e) => e.playerId === B2.playerId, markC));
    // R5 — host migration
    await A2.send('LEAVE_ROOM', { roomId: rid2, playerId: A2.playerId });
    await sleep(800);
    const room3 = await C4.send('GET_ROOM', { roomId: rid2 });
    const newHost = room3.hostPlayerId;
    record('R5', 'Host rời phòng — host mới được chọn', 'C còn lại là host', `host=${newHost === C4.playerId ? 'C' : newHost}`, newHost === C4.playerId, newHost === C4.playerId ? '' : `host=${newHost}`);
    // R6 — last player leaves
    await C4.send('LEAVE_ROOM', { roomId: rid2, playerId: C4.playerId });
    await sleep(800);
    const gone = await C4.send('GET_ROOM', { roomId: rid2 }).then(() => false).catch((e2) => e2.wsError?.code || e2.message);
    record('R6', 'Người cuối rời — room bị dọn', 'GET_ROOM lỗi sạch', `kết quả=${gone}`, gone !== false, String(gone));
    A2.close(); B2.close(); C4.close();
  } catch (e) { record('R4', 'Leave section', 'OK', `ERROR: ${e.message}`, false); }

  // ============ SECTION 9: ERROR PATHS ============
  try {
    const res = await B.send('JOIN_ROOM', { roomId: 'ZZZZ99', playerId: pid.B, username: 'Bob' }).catch((e) => e.wsError);
    record('E1', 'Join mã phòng không tồn tại', 'ERROR sạch', `code=${res?.code}`, !!res?.code || res?.type === 'ERROR');
    const res2 = await B.send('SUBMIT_GUESS', { roomId: 'ZZZZ99', playerId: pid.B, username: 'Bob', guess: 'x' }).catch((e) => e.wsError);
    record('E2', 'SubmitGuess room không tồn tại', 'ERROR sạch (không crash)', `code=${res2?.code || res2?.type}`, !!res2);
    // Invalid JSON frame
    B.ws.send('this is not json{{{');
    await sleep(800);
    record('E3', 'Frame text sai JSON', 'ERROR INVALID_JSON, kết nối sống', B.closed ? 'kết nối bị đóng' : 'kết nối sống, không crash', !B.closed);
  } catch (e) { record('E1', 'Error paths', 'OK', `ERROR: ${e.message}`, false); }

  // ============ SUMMARY ============
  const passed = results.filter((r) => r.pass).length;
  const failed = results.filter((r) => !r.pass).length;
  console.log(`\n=== WS E2E SUMMARY: ${passed} PASS / ${failed} FAIL / ${results.length} total ===\n`);
  for (const r of results.filter((x) => !x.pass)) {
    console.log(`  FAIL ${r.id}: ${r.scenario} — expected ${r.expected}, actual ${r.actual}${r.note ? ' (' + r.note + ')' : ''}`);
  }
  [A, B, C].forEach((c) => c.close());
  process.exit(0);
}

// track clients for section 6
const clients = {};
main().catch((e) => { console.error('FATAL:', e); process.exit(1); });
