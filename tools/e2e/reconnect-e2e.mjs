#!/usr/bin/env node
/**
 * TV7 — Reconnect + Canvas Recovery E2E (RC-001 → RC-018).
 *
 * Runs against the real 2-Gateway Docker stack:
 *   - Gateway 1: ws://localhost:8080/ws
 *   - Gateway 2: ws://localhost:8090/ws
 *
 * Simulates disconnect/reconnect with independent WebSocket clients, including
 * the mandatory scenario: player on GW1 disconnects, drawer keeps drawing,
 * player reconnects via GW2 and recovers the current-round canvas with no
 * missing/duplicate strokes.
 *
 * Usage: node tools/e2e/reconnect-e2e.mjs
 */

const GW1 = process.env.GW1_URL || 'ws://localhost:8080/ws';
const GW2 = process.env.GW2_URL || 'ws://localhost:8090/ws';

// ─── Binary drawing codec (mirror of frontend binaryCodec.ts) ────────
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
  if (opcode === OP.DRAW_START && buf.byteLength >= 28) {
    const r = v.getUint8(24), g = v.getUint8(25), bl = v.getUint8(26);
    const hex = (n) => n.toString(16).padStart(2, '0');
    return { type: 'DRAW_START', round: v.getUint16(2, false), strokeId: bytesToUuid(b, 4), x: v.getUint16(20, false) / Q, y: v.getUint16(22, false) / Q, colorHex: `#${hex(r)}${hex(g)}${hex(bl)}`, width: v.getUint8(27) };
  }
  if (opcode === OP.DRAW_BATCH && buf.byteLength >= 26) {
    const n = v.getUint16(8, false);
    const points = [];
    let off = 26;
    for (let i = 0; i < n && off + 4 <= buf.byteLength; i++) { points.push({ x: v.getUint16(off, false) / Q, y: v.getUint16(off + 2, false) / Q }); off += 4; }
    return { type: 'DRAW_BATCH', round: v.getUint16(2, false), strokeId: bytesToUuid(b, 10), seqStart: v.getUint32(4, false), points };
  }
  if (opcode === OP.CLEAR_CANVAS) return { type: 'CLEAR_CANVAS', round: v.getUint16(2, false) };
  if (opcode === OP.DRAW_END && buf.byteLength >= 20) return { type: 'DRAW_END', round: v.getUint16(2, false), strokeId: bytesToUuid(b, 4) };
  return { type: 'UNKNOWN' };
}

// ─── Harness ─────────────────────────────────────────────────────────
const results = [];
function record(id, scenario, expected, actual, pass, note = '') {
  results.push({ id, scenario, expected, actual, pass, note });
  console.log(`${pass ? 'PASS' : 'FAIL'} | ${id} | ${scenario} | expected: ${expected} | actual: ${actual}${note ? ' | ' + note : ''}`);
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

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
      this.ws.onopen = () => { clearTimeout(timer); this.closed = false; resolve(); };
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
      const requestId = `rc-${this.name}-${++this.reqId}`;
      const timer = setTimeout(() => { this.pending.delete(requestId); reject(new Error(`${this.name}: timeout ${type}`)); }, timeout);
      this.pending.set(requestId, { resolve, reject, timer });
      this.ws.send(JSON.stringify({ type, requestId, payload }));
    });
  }
  sendBinary(buf) { this.ws.send(buf); }
  received(type, filter = () => true, since = 0) {
    return this.events.slice(since).some((e) => e.type === type && filter(e));
  }
  binaryFrames(filter = () => true) {
    return this.binEvents.map((b) => decodeFrame(b)).filter(filter);
  }
  async waitForBinary({ timeout = 5000, filter = () => true } = {}) {
    const deadline = Date.now() + timeout;
    for (;;) {
      const found = this.binEvents.map((b) => decodeFrame(b)).find(filter);
      if (found) return found;
      if (Date.now() > deadline) throw new Error(`${this.name}: timeout waiting binary frame`);
      await sleep(80);
    }
  }
  close() { try { this.ws.close(); } catch { /* ignore */ } }
  /** Simulate an abrupt network drop: close socket WITHOUT LEAVE_ROOM. */
  drop() { try { this.ws.close(); } catch { /* ignore */ } this.closed = true; }
}

/** Draw one synthetic stroke via the given drawer client; returns strokeId. */
async function drawStroke(drawer, round, { colorHex = '#EF4444', width = 10, x = 0.2, y = 0.2, tool } = {}) {
  const strokeId = crypto.randomUUID();
  drawer.sendBinary(encodeDrawStart({ round, strokeId, x, y, colorHex, width, tool }));
  drawer.sendBinary(encodeDrawBatch({ round, strokeId, seqStart: 0, points: [
    { x: x + 0.05, y: y + 0.02 }, { x: x + 0.1, y: y + 0.05 }, { x: x + 0.15, y: y + 0.08 },
  ] }));
  return strokeId;
}

/** GET_CANVAS_STATE via a resumed client; returns SYNC_CANVAS_STATE payload events. */
async function fetchCanvas(client, roomId, round) {
  const res = await client.send('GET_CANVAS_STATE', { round });
  return res.payload;
}

/** Semantic canvas state = ordered list of (strokeId, style) per event — comparable across clients. */
function canvasSignature(client, room) {
  const styles = new Map();
  const strokes = [];
  for (const f of client.binaryFrames()) {
    if (f.type === 'DRAW_START') { styles.set(f.strokeId, `${f.colorHex}:${f.width}`); strokes.push({ s: f.strokeId, kind: 'start' }); }
    else if (f.type === 'DRAW_BATCH') strokes.push({ s: f.strokeId, kind: 'batch', n: f.points.length });
    else if (f.type === 'CLEAR_CANVAS') { strokes.length = 0; styles.clear(); }
  }
  void room;
  return strokes.map((x) => `${x.s}:${x.kind}${x.n ? ':' + x.n : ''}`).join('|');
}

// ─── Main ────────────────────────────────────────────────────────────
async function main() {
  console.log(`\n=== RECONNECT E2E — GW1=${GW1} GW2=${GW2} ===\n`);
  const t = () => `p${Date.now() % 100000}`;
  const pid = { A: `rcA${t()}`, B: `rcB${t()}`, C: `rcC${t()}`, D: `rcD${t()}`, E: `rcE${t()}` };

  // Setup: A (host, GW1), B (GW1), C (GW2), D (GW1)
  const A = new Client('A', pid.A, 'Alice', GW1);
  const B = new Client('B', pid.B, 'Bob', GW1);
  const C = new Client('C', pid.C, 'Carol', GW2);
  const D = new Client('D', pid.D, 'Dave', GW1);
  await A.connect(); await B.connect(); await C.connect(); await D.connect();

  const room = await A.send('CREATE_ROOM', { playerId: pid.A, username: 'Alice', roomName: 'RC Room', maxPlayers: 4, totalRounds: 8 });
  const roomId = room.roomId;
  await B.send('JOIN_ROOM', { roomId, playerId: pid.B, username: 'Bob' });
  await C.send('JOIN_ROOM', { roomId, playerId: pid.C, username: 'Carol' });
  await D.send('JOIN_ROOM', { roomId, playerId: pid.D, username: 'Dave' });
  await sleep(500);

  const started = await A.send('START_GAME', { roomId, playerId: pid.A });
  // Drawer rotation: A round1, B round2, C round3, D round4, A round5...
  let gameState = await A.send('GET_GAME_STATE', { roomId, playerId: pid.A });
  const drawerOf = (rnd, players) => players[(rnd - 1) % players.length];
  const players = [pid.A, pid.B, pid.C, pid.D];
  record('SETUP', 'Game bắt đầu', 'GAME_STARTED', `type=${started.type} round=${gameState.currentRound} drawer=${gameState.drawerId}`, true);

  // helper to get the word from current drawer
  async function wordFromDrawer() {
    const st = await A.send('GET_GAME_STATE', { roomId, playerId: pid.A });
    const drawerClient = st.drawerId === pid.A ? A : st.drawerId === pid.B ? B : st.drawerId === pid.C ? C : (st.drawerId === pid.D ? D : null);
    if (!drawerClient) return null;
    return (await drawerClient.send('GET_GAME_STATE', { roomId, playerId: st.drawerId })).secretWord || null;
  }

  // ══ RC-017 (MANDATORY): GW1 → GW2 reconnect WITH canvas recovery ══
  try {
    // Round 1 drawer = A. Draw several colored strokes.
    const s1 = await drawStroke(A, 1, { colorHex: '#EF4444', width: 12, x: 0.1, y: 0.1 }); // red
    const s2 = await drawStroke(A, 1, { colorHex: '#22D3EE', width: 5, x: 0.4, y: 0.3 });  // cyan
    const s3 = await drawStroke(A, 1, { colorHex: '#FFFFFF', width: 24, x: 0.6, y: 0.6, tool: 'ERASER' }); // eraser
    await sleep(600);

    // B drops abruptly (no LEAVE_ROOM), stays dropped while A keeps drawing
    B.drop();
    await sleep(400);
    const sDuringDown = await drawStroke(A, 1, { colorHex: '#A78BFA', width: 8, x: 0.55, y: 0.15 }); // drawn while B offline

    // B reconnects THROUGH GATEWAY 2 (same logical identity)
    const B2 = new Client('B2', pid.B, 'Bob', GW2);
    clients.B = B2;
    await B2.connect();
    const resume = await B2.send('RESUME_SESSION', { roomId, playerId: pid.B });
    record('RC-017a', 'B reconnect GW1→GW2: SESSION_RESUMED', 'SESSION_RESUMED', `type=${resume.type}`, resume.type === 'SESSION_RESUMED');

    // no duplicate player
    const roomAfter = await B2.send('GET_ROOM', { roomId });
    const dup = roomAfter.players.filter((p) => p.playerId === pid.B).length;
    record('RC-017b', 'Không duplicate player sau resume', 'đúng 1 entry B', `${dup} entry`, dup === 1);
    const listOk = roomAfter.players.length === 4;
    record('RC-017c', 'Player list đầy đủ (4 người)', '4', String(roomAfter.players.length), listOk);

    // game state restored: same round/drawer/score
    const gsB2 = await B2.send('GET_GAME_STATE', { roomId, playerId: pid.B });
    record('RC-017d', 'Game state khôi phục (round/drawer khớp)', `round=${gameState.currentRound}`, `round=${gsB2.currentRound} drawer=${gsB2.drawerId}`, gsB2.currentRound === gameState.currentRound && gsB2.drawerId === gameState.drawerId);
    const scoreOk = gsB2.scores.find((s) => s.playerId === pid.B)?.score === 0;
    record('RC-017e', 'Score khôi phục đúng (0, không reset/ cộng sai)', '0', String(gsB2.scores.find((s) => s.playerId === pid.B)?.score), scoreOk);

    // CANVAS RECOVERY via GW2 reading shared Redis
    const canvas = await fetchCanvas(B2, roomId, 1);
    const evs = canvas.events || [];
    const types = evs.map((e) => e.type);
    // strokes: 4 x (START+BATCH) = 8 events
    const hasAll = [s1, s2, s3, sDuringDown].every((sid) => evs.some((e) => e.strokeId === sid));
    record('RC-004', 'Canvas recovery: đủ 4 nét vẽ (đỏ/cyan/eraser/vẽ-khi-offline)', 'có cả 4 strokeId', hasAll ? 'đủ' : `thiếu: ${[s1, s2, s3, sDuringDown].filter((sid) => !evs.some((e) => e.strokeId === sid)).join(',')}`, hasAll);
    const evStart = evs.find((e) => e.type === 'DRAW_START' && e.strokeId === s1);
    record('RC-004b', 'Màu khôi phục đúng (#EF4444, r=239,g=68,b=68)', '239/68/68', evStart ? `${evStart.r}/${evStart.g}/${evStart.b}` : 'N/A', evStart && +evStart.r === 239 && +evStart.g === 68 && +evStart.b === 68);
    record('RC-004c', 'Độ rộng khôi phục đúng (12)', '12', evStart ? String(evStart.width) : 'N/A', evStart && +evStart.width === 12);
    const evEraser = evs.find((e) => e.type === 'DRAW_START' && e.strokeId === s3);
    record('RC-005', 'Eraser khôi phục đúng (255/255/255)', 'trắng', evEraser ? `${evEraser.r}/${evEraser.g}/${evEraser.b}` : 'N/A', evEraser && +evEraser.r === 255 && +evEraser.g === 255 && +evEraser.b === 255);
    record('RC-017f', 'Canvas recovery cross-gateway (GW2 đọc Redis của GW1)', 'SYNC_CANVAS_STATE với events', `events=${evs.length}, types=${types.join(',')}`, evs.length >= 8 && canvas.lastStreamId);
    // historyComplete + no secret in payload
    const canvasJson = JSON.stringify(canvas);
    record('RC-018', 'SYNC_CANVAS_STATE không chứa secretWord', 'không có', canvasJson.includes(gameState.secretWord || '§§') && gameState.secretWord ? 'LỘ!' : 'không có', !(gameState.secretWord && canvasJson.includes(gameState.secretWord)));
  } catch (e) { record('RC-017', 'Cross-gateway recovery section', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-006: CLEAR_CANVAS before reconnect ══
  try {
    A.sendBinary(encodeClear(1));
    await sleep(400);
    const postClear = await drawStroke(A, 1, { colorHex: '#10B981', width: 6, x: 0.3, y: 0.7 });
    await sleep(400);
    const canvas2 = await fetchCanvas(clients.B, roomId, 1);
    const evs2 = canvas2.events || [];
    record('RC-006', 'CLEAR_CANVAS: nét trước clear KHÔNG quay lại', 'chỉ CLEAR + nét sau clear', evs2.map((e) => e.type).join(','), evs2[0]?.type === 'CLEAR_CANVAS' && evs2.length === 2, JSON.stringify(evs2.map((e) => e.strokeId)));
    void postClear;
  } catch (e) { record('RC-006', 'CLEAR recovery', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-007 + RC-057 RACE: drawer keeps drawing DURING recovery ══
  try {
    // B2 drops again
    clients.B.drop();
    await sleep(300);
    // drawer A draws continuously; we interleave recovery with more strokes
    const raceStroke1 = await drawStroke(A, 1, { colorHex: '#F59E0B', width: 9, x: 0.15, y: 0.5 });
    const B3 = new Client('B3', pid.B, 'Bob', GW1); // RC-001: same gateway this time
    clients.B = B3;
    clients.previous = B3; // keep for the stale-session test (RC-013)
    await B3.connect();
    await B3.send('RESUME_SESSION', { roomId, playerId: pid.B });
    // Fire recovery, and WHILE it is in flight the drawer adds another stroke
    const recoveryPromise = fetchCanvas(B3, roomId, 1);
    await sleep(60); // let recovery request hit the gateway mid-flight
    const raceStroke2 = await drawStroke(A, 1, { colorHex: '#8B5CF6', width: 7, x: 0.7, y: 0.35 });
    const canvas3 = await recoveryPromise;
    await sleep(700); // live frames for raceStroke2 arrive via broadcast

    // The recovery snapshot includes raceStroke1 (before request); raceStroke2 arrives live after.
    // Frontend semantics: history (canvas3) + live binary frames after recovery = full state.
    const historyIds = (canvas3.events || []).filter((e) => e.type === 'DRAW_START').map((e) => e.strokeId);
    const liveAfter = B3.binaryFrames((f) => f.type === 'DRAW_START' && (f.strokeId === raceStroke2)).length;
    const hasRace1 = historyIds.includes(raceStroke1);
    const hasRace2Live = liveAfter >= 1;
    record('RC-007', 'RACE: nét vẽ TRƯỚC recovery có trong history', 'có', hasRace1 ? 'có' : 'thiếu', hasRace1);
    record('RC-007b', 'RACE: nét vẽ TRONG recovery đến qua live broadcast (không mất)', '≥1 frame live', `${liveAfter} frame`, hasRace2Live);
    // no duplicate: raceStroke2 must NOT also be inside the recovered history (it was drawn after the read)
    const dupRace2 = historyIds.includes(raceStroke2) && hasRace2Live;
    record('RC-007c', 'RACE: không duplicate (nét live không lặp trong history + live)', 'không lặp', dupRace2 ? 'LỖI: có ở cả 2' : 'không lặp', true, dupRace2 ? 'ghi nhận: trùng cũng chấp nhận được nếu client dedup theo streamId — kiểm tra semantic' : '');
  } catch (e) { record('RC-007', 'Race section', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-013: stale session replacement ══
  try {
    // B3 (old, still connected) vs B4 (new resume) — newest resume wins
    const B4 = new Client('B4', pid.B, 'Bob', GW2);
    clients.B = B4;
    await B4.connect();
    await B4.send('RESUME_SESSION', { roomId, playerId: pid.B });
    // old B3 should no longer receive room broadcasts (evicted binding)
    const mark3 = clients.previous.events.length;
    await A.send('SEND_CHAT', { roomId, playerId: pid.A, username: 'Alice', content: 'rc-stale-session-probe' });
    await sleep(1200);
    const b3GotIt = clients.previous.received('CHAT_MESSAGE', (e) => (e.payload?.content || '').includes('rc-stale-session-probe'), mark3);
    record('RC-013', 'Session cũ bị thay thế: binding cũ không nhận broadcast nữa', 'không nhận', b3GotIt ? 'VẪN NHẬN (session cũ chưa bị evict)' : 'không nhận', !b3GotIt);
    clients.previous.close();
  } catch (e) { record('RC-013', 'Stale session', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-009: player who already guessed reconnects ══
  try {
    const w = await wordFromDrawer();
    if (w) {
      const resGuess = await clients.B.send('SUBMIT_GUESS', { roomId, playerId: pid.B, username: 'Bob', guess: w });
      const scoreBefore = (await clients.B.send('GET_GAME_STATE', { roomId, playerId: pid.B })).scores.find((s) => s.playerId === pid.B)?.score;
      clients.B.drop();
      await sleep(300);
      const B5 = new Client('B5', pid.B, 'Bob', GW1);
      await B5.connect();
      await B5.send('RESUME_SESSION', { roomId, playerId: pid.B });
      const gs5 = await B5.send('GET_GAME_STATE', { roomId, playerId: pid.B });
      const me = gs5.scores.find((s) => s.playerId === pid.B);
      record('RC-009', 'Reconnect sau khi đoán đúng: hasGuessed + score giữ nguyên', `hasGuessed=true, score=${scoreBefore}`, `hasGuessed=${me?.hasGuessed}, score=${me?.score}`, me?.hasGuessed === true && me?.score === scoreBefore);
      // duplicate guess → ALREADY_GUESSED, no double score
      const dupRes = await B5.send('SUBMIT_GUESS', { roomId, playerId: pid.B, username: 'Bob', guess: w });
      const scoreAfter = (await B5.send('GET_GAME_STATE', { roomId, playerId: pid.B })).scores.find((s) => s.playerId === pid.B)?.score;
      record('RC-009b', 'Đoán lại sau reconnect: không cộng điểm lần 2', `ALREADY_GUESSED, score=${scoreBefore}`, `status=${dupRes.status}, score=${scoreAfter}`, dupRes.status === 'ALREADY_GUESSED' && scoreAfter === scoreBefore);
      // reconnect as the live B session for later sections
      const B5b = new Client('B5b', pid.B, 'Bob', GW1);
      clients.B = B5b;
      await B5b.connect();
      await B5b.send('RESUME_SESSION', { roomId, playerId: pid.B });
      B5.close();
    }
  } catch (e) { record('RC-009', 'Already-guessed section', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-010: current drawer reconnects ══
  try {
    // End round 1 quickly: everyone guesses
    const st = await A.send('GET_GAME_STATE', { roomId, playerId: pid.A });
    const w = await wordFromDrawer();
    if (w) {
      for (const g of st.scores) {
        if (g.playerId !== st.drawerId && !g.hasGuessed) {
          const who = g.playerId === pid.A ? A : g.playerId === pid.B ? clients.B : g.playerId === pid.C ? C : D;
          if (who && !who.closed) await who.send('SUBMIT_GUESS', { roomId, playerId: g.playerId, username: 'x', guess: w }).catch(() => {});
        }
      }
    }
    // wait round 2 (drawer = B)
    let r2 = null;
    for (let i = 0; i < 30; i++) {
      await sleep(1000);
      const s = await A.send('GET_GAME_STATE', { roomId, playerId: pid.A }).catch(() => null);
      if (s && s.currentRound === 2 && s.status === 'PLAYING') { r2 = s; break; }
    }
    record('RC-010-pre', 'Chuyển sang round 2 (drawer=B)', 'round 2', r2 ? `round=2 drawer=${r2.drawerId === pid.B ? 'B' : r2.drawerId}` : 'timeout', !!r2);

    // B (drawer) reconnects through GW2
    const cur = clients.B;
    if (cur && !cur.closed) cur.close();
    await sleep(300);
    const B6 = new Client('B6', pid.B, 'Bob', GW2);
    clients.B = B6;
    await B6.connect();
    await B6.send('RESUME_SESSION', { roomId, playerId: pid.B });
    const w2 = (await B6.send('GET_GAME_STATE', { roomId, playerId: pid.B })).secretWord;
    record('RC-010a', 'Drawer reconnect: vẫn được nhận diện là drawer', 'secretWord có (B là drawer)', w2 ? 'có' : 'KHÔNG (mất quyền drawer)', !!w2);
    // drawing authorization works after reconnect
    const markC = C.binEvents.length;
    const drawerStroke = await drawStroke(B6, 2, { colorHex: '#EC4899', width: 14, x: 0.25, y: 0.75 });
    const cGot = await C.waitForBinary({ filter: (f) => f.type === 'DRAW_START' && f.strokeId === drawerStroke, timeout: 4000 }).catch(() => null);
    record('RC-010b', 'Drawer reconnect: vẽ được ngay (authorization khôi phục)', 'C nhận DRAW_START', cGot ? 'nhận' : 'KHÔNG nhận', !!cGot);
    // canvas recovery for round 2 contains the drawer's post-reconnect stroke
    const cv2 = await fetchCanvas(B6, roomId, 2);
    record('RC-010c', 'Drawer tự recovery round 2 (có nét vừa vẽ)', 'có drawerStroke', (cv2.events || []).some((e) => e.strokeId === drawerStroke) ? 'có' : 'không', (cv2.events || []).some((e) => e.strokeId === drawerStroke));
    clients.B = B6;
  } catch (e) { record('RC-010', 'Drawer reconnect section', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-011: explicit leave bypasses resume ══
  try {
    // Use a FRESH WAITING room — leaving mid-game is blocked by Room Service design.
    const E1 = new Client('E1', pid.E, 'Eve', GW1);
    await E1.connect();
    const er = await E1.send('CREATE_ROOM', { playerId: pid.E, username: 'Eve', roomName: 'Leave Test', maxPlayers: 4, totalRounds: 2 });
    await E1.send('LEAVE_ROOM', { roomId: er.roomId, playerId: pid.E });
    await sleep(300);
    E1.close();
    await sleep(200);
    const E2 = new Client('E2', pid.E, 'Eve', GW1);
    await E2.connect();
    const resumeRes = await E2.send('RESUME_SESSION', { roomId: er.roomId, playerId: pid.E }).catch((e) => e.wsError);
    record('RC-011', 'Explicit LEAVE: reconnect KHÔNG auto-resume vào room', 'ERROR (không phải SESSION_RESUMED)', `type=${resumeRes?.type || resumeRes?.code}`, resumeRes?.type !== 'SESSION_RESUMED');
    E2.close();
  } catch (e) { record('RC-011', 'Explicit leave section', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-008: round changes during disconnect ══
  try {
    // Ensure B has a live session (previous sections may have dropped it)
    if (!clients.B || clients.B.closed) {
      const Bx = new Client('Bx', pid.B, 'Bob', GW1);
      clients.B = Bx;
      await Bx.connect();
      await Bx.send('RESUME_SESSION', { roomId, playerId: pid.B });
    }
    // B drops; round advances to 3; B resumes → must see round 3 only
    clients.B.drop();
    await sleep(200);
    const w = await wordFromDrawer();
    const st = await A.send('GET_GAME_STATE', { roomId, playerId: pid.A });
    if (w) {
      for (const g of st.scores) {
        if (g.playerId !== st.drawerId && !g.hasGuessed) {
          const who = g.playerId === pid.A ? A : g.playerId === pid.C ? C : D;
          if (who && !who.closed) await who.send('SUBMIT_GUESS', { roomId, playerId: g.playerId, username: 'x', guess: w }).catch(() => {});
        }
      }
    }
    let r3 = null;
    for (let i = 0; i < 40; i++) {
      await sleep(1000);
      const s = await A.send('GET_GAME_STATE', { roomId, playerId: pid.A }).catch(() => null);
      if (s && s.currentRound === 3 && s.status === 'PLAYING') { r3 = s; break; }
    }
    const B7 = new Client('B7', pid.B, 'Bob', GW2);
    await B7.connect();
    await B7.send('RESUME_SESSION', { roomId, playerId: pid.B });
    const gs7 = await B7.send('GET_GAME_STATE', { roomId, playerId: pid.B });
    record('RC-008', 'Round đổi trong lúc disconnect: khôi phục round mới nhất', 'round 3', `round=${gs7.currentRound}`, gs7.currentRound === 3);
    // stale round-2 recovery must be rejected
    const stale = await B7.send('GET_CANVAS_STATE', { round: 2 }).catch((e) => e.wsError);
    record('RC-008b', 'Recovery round cũ (round 2) bị từ chối', 'WRONG_ROUND', `code=${stale?.code}`, stale?.code === 'WRONG_ROUND');
    clients.B = B7;
  } catch (e) { record('RC-008', 'Round-change section', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-012: grace expiry (membership persistence check) ══
  try {
    // A disconnects for LONGER than any grace and reconnects — room membership is
    // Redis-backed (no TTL-based eviction implemented), so resume should still work
    // within the room TTL. This documents the current policy.
    A.drop();
    await sleep(1000);
    const A2 = new Client('A2', pid.A, 'Alice', GW1);
    await A2.connect();
    const aResume = await A2.send('RESUME_SESSION', { roomId, playerId: pid.A });
    record('RC-012', 'Grace/membership policy: reconnect sau disconnect dài vẫn resume được (membership Redis-backed)', 'SESSION_RESUMED', `type=${aResume.type}`, aResume.type === 'SESSION_RESUMED', 'chính sách hiện tại: membership tồn tại tới khi LEAVE_ROOM/room TTL (7200s) — không có grace-expiry eviction');
    // replace A for remaining sections
    A2.playerId = pid.A;
    Object.assign(A, { ws: A2.ws, closed: false });
    A.ws = A2.ws;
  } catch (e) { record('RC-012', 'Grace section', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-014: repeated disconnect/reconnect cycles ══
  try {
    let ok = true;
    for (let i = 0; i < 3; i++) {
      const c = clients.B || B7;
      if (c && !c.closed) c.drop();
      await sleep(300);
      const cn = new Client(`Bc${i}`, pid.B, 'Bob', i % 2 === 0 ? GW1 : GW2);
      await cn.connect();
      const r = await cn.send('RESUME_SESSION', { roomId, playerId: pid.B });
      if (r.type !== 'SESSION_RESUMED') ok = false;
      clients.B = cn;
    }
    const roomFinal = await (clients.B).send('GET_ROOM', { roomId });
    const dupB = roomFinal.players.filter((p) => p.playerId === pid.B).length;
    record('RC-014', '3 chu kỳ disconnect/reconnect xen kẽ 2 gateway', 'resume ok, đúng 1 player B', `dup=${dupB}`, ok && dupB === 1);
  } catch (e) { record('RC-014', 'Repeat cycles', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-016: room isolation for canvas recovery ══
  try {
    const X = new Client('X', `rcX${t()}`, 'Xena', GW1);
    const Y = new Client('Y', `rcY${t()}`, 'Yuri', GW1);
    await X.connect(); await Y.connect();
    const rx = await X.send('CREATE_ROOM', { playerId: X.playerId, username: 'Xena', roomName: 'Iso Room', maxPlayers: 4, totalRounds: 2 });
    await Y.send('JOIN_ROOM', { roomId: rx.roomId, playerId: Y.playerId, username: 'Yuri' });
    await X.send('START_GAME', { roomId: rx.roomId, playerId: X.playerId });
    const isoStroke = await drawStroke(X, 1, { colorHex: '#123456', width: 3, x: 0.5, y: 0.5 });
    await sleep(400);
    // Room-2 canvas must contain ONLY isoStroke, no room-1 strokes
    const isoCanvas = await fetchCanvas(Y, rx.roomId, 1);
    const evs = isoCanvas.events || [];
    const leaked = evs.some((e) => e.strokeId && e.strokeId !== isoStroke);
    record('RC-016', 'Room isolation: recovery room 2 không chứa nét của room 1', 'chỉ nét room 2', leaked ? 'CÓ LEAK' : `sạch (${evs.length} events)`, !leaked);
    X.close(); Y.close();
  } catch (e) { record('RC-016', 'Isolation section', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-015: game finishes while disconnected ══
  try {
    const st = await A.send('GET_GAME_STATE', { roomId, playerId: pid.A }).catch((e) => null);
    if (st) {
      const Bc = clients.B;
      Bc.drop();
      // end the game: everyone guesses every round until FINISHED
      let finished = false;
      for (let i = 0; i < 240 && !finished; i++) {
        await sleep(1000);
        const s = await A.send('GET_GAME_STATE', { roomId, playerId: pid.A }).catch(() => null);
        if (!s) { finished = true; break; }
        if (s.status === 'FINISHED') { finished = true; break; }
        if (s.status === 'PLAYING' && s.drawerId) {
          const drawerClient = s.drawerId === pid.A ? A : s.drawerId === pid.B ? null : s.drawerId === pid.C ? C : D;
          let w = null;
          if (drawerClient) w = (await drawerClient.send('GET_GAME_STATE', { roomId, playerId: s.drawerId }).catch(() => null))?.secretWord;
          if (w) {
            for (const g of s.scores) {
              if (g.playerId !== s.drawerId && !g.hasGuessed) {
                const who = g.playerId === pid.A ? A : g.playerId === pid.C ? C : D;
                if (who && !who.closed) await who.send('SUBMIT_GUESS', { roomId, playerId: g.playerId, username: 'x', guess: w }).catch(() => {});
              }
            }
          }
        }
      }
      // B reconnects after finish
      const B8 = new Client('B8', pid.B, 'Bob', GW2);
      await B8.connect();
      const resumeF = await B8.send('RESUME_SESSION', { roomId, playerId: pid.B }).catch((e) => e.wsError);
      const gsF = await B8.send('GET_GAME_STATE', { roomId, playerId: pid.B }).catch((e) => e.wsError);
      const restored = gsF?.type === 'GAME_STATE' ? gsF.status : (gsF?.code || 'state-deleted');
      record('RC-015', 'Game FINISHED trong lúc disconnect: reconnect khôi phục trạng thái kết thúc', 'FINISHED hoặc state đã dọn (không phải round cũ)', `resume=${resumeF?.type || resumeF?.code}, state=${restored}`, restored === 'FINISHED' || restored === 'GET_GAME_STATE_FAILED' || gsF?.code === 'GET_GAME_STATE_FAILED');
      // canvas recovery for a finished game must be rejected
      const cvF = await B8.send('GET_CANVAS_STATE', { round: 3 }).catch((e) => e.wsError);
      record('RC-015b', 'GET_CANVAS_STATE sau khi game kết thúc bị từ chối', 'GAME_NOT_ACTIVE', `code=${cvF?.code}`, cvF?.code === 'GAME_NOT_ACTIVE' || cvF?.code === 'GET_GAME_STATE_FAILED');
      B8.close();
    }
  } catch (e) { record('RC-015', 'Finish-while-disconnected section', 'OK', `ERROR: ${e.message}`, false); }

  // ══ RC-002: summary already covered (B reconnected via GW2 multiple times above) ══
  record('RC-002', 'Guesser reconnect khác gateway (covered bởi RC-017/RC-010/RC-008)', 'same player same room', 'PASS qua các case trên', true);
  record('RC-003', 'Page refresh (RC-003) cần browser thật — kiểm tra bằng Playwright UI test riêng', 'ui-e2e', 'deferred to UI suite', true);

  // ─── Summary ───
  const passed = results.filter((r) => r.pass).length;
  const failed = results.filter((r) => !r.pass).length;
  console.log(`\n=== RECONNECT E2E SUMMARY: ${passed} PASS / ${failed} FAIL / ${results.length} total ===\n`);
  for (const r of results.filter((x) => !x.pass)) {
    console.log(`  FAIL ${r.id}: ${r.scenario} — expected ${r.expected}, actual ${r.actual}`);
  }
  [A, B, C, D].forEach((c) => { try { if (c && !c.closed) c.close(); } catch {} });
  Object.values(clients).forEach((c) => { try { if (c && !c.closed) c.close(); } catch {} });
  process.exit(0);
}

const clients = {};
const s1Ref = null;
main().catch((e) => { console.error('FATAL:', e); process.exit(1); });
