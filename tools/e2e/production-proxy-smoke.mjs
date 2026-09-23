#!/usr/bin/env node
/**
 * Local production-proxy smoke test: create/join/ready/start, binary drawing,
 * guessing/chat, next round, reconnect/canvas recovery, optional Gateway-1
 * process failover, and rematch, all through one Caddy WebSocket URL.
 *
 * Native Node WebSocket does not send a browser Origin header. Run only against
 * an isolated local validation stack with WS_ALLOWED_ORIGINS blank/whitespace.
 * Failover is opt-in and guarded by a Compose project name containing
 * "validation" so this script cannot stop a normal deployment by accident.
 */

import { execFileSync } from 'node:child_process';

const WS_URL = process.env.GW_URL || 'ws://127.0.0.1:18080/ws';
const ALLOW_NO_ORIGIN = process.env.PROD_SMOKE_ALLOW_NO_ORIGIN === '1';
const TEST_FAILOVER = process.env.PROD_SMOKE_TEST_FAILOVER === '1';
const PROJECT = process.env.PROD_SMOKE_COMPOSE_PROJECT || '';
const COMPOSE_ENV = process.env.PROD_SMOKE_COMPOSE_ENV || '.env.production.example';
const results = [];
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function record(name, ok, detail = '') {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${name}${detail ? ` | ${detail}` : ''}`);
  if (!ok) throw new Error(`${name} failed${detail ? `: ${detail}` : ''}`);
}

class Client {
  constructor(name) {
    this.name = name;
    this.ws = null;
    this.seq = 0;
    this.pending = new Map();
    this.events = [];
    this.frames = [];
    this.sessionToken = null;
    this.gatewayId = null;
    this.closed = false;
  }

  async connect(timeoutMs = 8000) {
    this.ws = new WebSocket(WS_URL);
    this.ws.binaryType = 'arraybuffer';
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`${this.name}: connect timeout`)), timeoutMs);
      this.ws.onopen = () => { clearTimeout(timer); resolve(); };
      this.ws.onerror = () => { clearTimeout(timer); reject(new Error(`${this.name}: WebSocket error`)); };
    });
    this.ws.onclose = () => { this.closed = true; };
    this.ws.onmessage = (event) => {
      if (event.data instanceof ArrayBuffer) {
        this.frames.push(event.data);
        return;
      }
      let message;
      try { message = JSON.parse(event.data); } catch { return; }
      this.events.push(message);
      if (message.sessionToken) this.sessionToken = message.sessionToken;
      if (message.gatewayId) this.gatewayId = message.gatewayId;
      if (message.requestId && this.pending.has(message.requestId)) {
        const request = this.pending.get(message.requestId);
        this.pending.delete(message.requestId);
        clearTimeout(request.timer);
        if (message.type === 'ERROR') request.reject(Object.assign(new Error(message.code || message.message), { response: message }));
        else request.resolve(message);
      }
    };
  }

  send(type, payload = {}, timeoutMs = 10000) {
    const requestId = `prod-smoke-${this.name}-${++this.seq}`;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(requestId);
        reject(new Error(`${this.name}: ${type} timed out`));
      }, timeoutMs);
      this.pending.set(requestId, { resolve, reject, timer });
      this.ws.send(JSON.stringify({ type, requestId, payload }));
    });
  }

  async ping() {
    const response = await this.send('PING');
    this.gatewayId = response.gatewayId;
    return this.gatewayId;
  }

  async waitFor(type, timeoutMs = 8000, filter = () => true) {
    const until = Date.now() + timeoutMs;
    while (Date.now() < until) {
      const message = this.events.find((event) => event.type === type && filter(event));
      if (message) return message;
      await sleep(50);
    }
    throw new Error(`${this.name}: waiting for ${type} timed out`);
  }

  async waitForBinary(round, timeoutMs = 5000) {
    const until = Date.now() + timeoutMs;
    while (Date.now() < until) {
      const match = this.frames.find((frame) => {
        const view = new DataView(frame);
        return view.byteLength >= 4 && view.getUint8(0) === 1 && view.getUint8(1) === 1 && view.getUint16(2, false) === round;
      });
      if (match) return match;
      await sleep(50);
    }
    throw new Error(`${this.name}: no DRAW_START frame for round ${round}`);
  }

  close() {
    try { this.ws?.close(); } catch { /* already closed */ }
  }
}

async function connectToGateway(name, wantedGateway, attempts = 16) {
  for (let attempt = 0; attempt < attempts; attempt++) {
    const client = new Client(`${name}-${attempt + 1}`);
    try {
      await client.connect();
      const gateway = await client.ping();
      if (gateway === wantedGateway) return client;
      client.close();
      await sleep(80);
    } catch (error) {
      client.close();
      if (attempt + 1 === attempts) throw error;
      await sleep(250);
    }
  }
  throw new Error(`Caddy did not route a fresh connection to ${wantedGateway}`);
}

function encodeStart(round, strokeId) {
  const buffer = new ArrayBuffer(28);
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);
  const hex = strokeId.replaceAll('-', '');
  view.setUint8(0, 1); view.setUint8(1, 1); view.setUint16(2, round, false);
  for (let index = 0; index < 16; index++) bytes[4 + index] = parseInt(hex.slice(index * 2, index * 2 + 2), 16);
  view.setUint16(20, 12000, false); view.setUint16(22, 22000, false);
  view.setUint8(24, 239); view.setUint8(25, 68); view.setUint8(26, 68); view.setUint8(27, 8);
  return buffer;
}

function encodeBatch(round, strokeId) {
  const points = [{ x: 18000, y: 28000 }, { x: 24000, y: 36000 }, { x: 32000, y: 44000 }];
  const buffer = new ArrayBuffer(26 + points.length * 4);
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);
  const hex = strokeId.replaceAll('-', '');
  view.setUint8(0, 1); view.setUint8(1, 2); view.setUint16(2, round, false);
  view.setUint32(4, 0, false); view.setUint16(8, points.length, false);
  for (let index = 0; index < 16; index++) bytes[10 + index] = parseInt(hex.slice(index * 2, index * 2 + 2), 16);
  points.forEach((point, index) => {
    view.setUint16(26 + index * 4, point.x, false);
    view.setUint16(28 + index * 4, point.y, false);
  });
  return buffer;
}

async function draw(client, round) {
  const strokeId = crypto.randomUUID();
  client.ws.send(encodeStart(round, strokeId));
  client.ws.send(encodeBatch(round, strokeId));
  return strokeId;
}

async function gameState(client, roomId, playerId) {
  return client.send('GET_GAME_STATE', { roomId, playerId });
}

async function waitForPhase(clients, roomId, round, phase, timeoutMs = 15000) {
  const until = Date.now() + timeoutMs;
  while (Date.now() < until) {
    for (const client of clients) {
      const state = await gameState(client, roomId, client.playerId).catch(() => null);
      if (state?.currentRound === round && state?.roundPhase === phase) return state;
    }
    await sleep(350);
  }
  throw new Error(`round ${round} did not reach ${phase}`);
}

function compose(action) {
  if (!PROJECT || !/validation/i.test(PROJECT)) throw new Error('Failover test requires PROD_SMOKE_COMPOSE_PROJECT containing "validation"');
  const args = [
    'compose', '--env-file', COMPOSE_ENV,
    '-f', 'docker-compose.yml', '-f', 'docker-compose.prod.yml', '-f', 'docker-compose.http-test.yml',
    '--profile', 'multi-gateway', '--project-name', PROJECT, action, 'realtime-gateway',
  ];
  execFileSync('docker', args, { stdio: 'inherit' });
}

async function run() {
  if (!ALLOW_NO_ORIGIN) throw new Error('Refusing to use Node WebSocket without Origin; set PROD_SMOKE_ALLOW_NO_ORIGIN=1 only for an isolated local stack.');
  if (TEST_FAILOVER && !PROJECT) throw new Error('Set PROD_SMOKE_COMPOSE_PROJECT to the isolated validation project before enabling failover.');

  let host, guest, resumedHost;
  let gateway1Stopped = false;
  try {
    host = await connectToGateway('host', 'gateway-1');
    guest = await connectToGateway('guest', 'gateway-2');
    host.playerId = `proxy-host-${Date.now()}`;
    guest.playerId = `proxy-guest-${Date.now()}`;
    record('Caddy routes live sockets to both gateways', host.gatewayId === 'gateway-1' && guest.gatewayId === 'gateway-2');

    const room = await host.send('CREATE_ROOM', {
      playerId: host.playerId, username: 'ProxyHost', roomName: 'Production Proxy Smoke',
      maxPlayers: 2, totalRounds: 2,
    });
    const roomId = room.roomId;
    const joined = await guest.send('JOIN_ROOM', { roomId, playerId: guest.playerId, username: 'ProxyGuest' });
    host.roomId = guest.roomId = roomId;
    record('Create/join room over WSS proxy', room.type === 'ROOM_CREATED' && joined.type === 'ROOM_JOINED', `room=${roomId}`);

    await guest.send('SET_READY', { ready: true });
    const started = await host.send('START_GAME', { roomId });
    record('Ready gate and game start', started.type === 'GAME_STARTED');

    let stateHost = await gameState(host, roomId, host.playerId);
    let drawer = stateHost.drawerId === host.playerId ? host : guest;
    let drawerState = await gameState(drawer, roomId, drawer.playerId);
    if (drawerState.roundPhase === 'WORD_SELECTION') {
      await drawer.send('SELECT_WORD', { roomId, choiceId: drawerState.wordChoices[0].choiceId });
    }
    await waitForPhase([host, guest], roomId, 1, 'DRAWING');
    drawerState = await gameState(drawer, roomId, drawer.playerId);
    const secret1 = drawerState.secretWord;
    const stroke1 = await draw(drawer, 1);
    const other1 = drawer === host ? guest : host;
    await other1.waitForBinary(1);
    record('Binary drawing fans out across gateways through Caddy', true, `drawer=${drawer.gatewayId}, receiver=${other1.gatewayId}`);

    const snapshot1 = await other1.send('GET_CANVAS_STATE', { round: 1 });
    const canvas1 = snapshot1.payload;
    record('Canvas recovery reads the remote gateway stream', canvas1?.events?.some((event) => event.strokeId === stroke1));

    const chatText = `proxy-chat-${Date.now()}`;
    const chatMark = other1.events.length;
    await host.send('SEND_CHAT', { roomId, content: chatText });
    await other1.waitFor('CHAT_MESSAGE', 5000, (event) => (event.content || event.payload?.content) === chatText);
    record('Cross-gateway chat fanout', other1.events.length > chatMark);

    const guesser = drawer === host ? guest : host;
    const guessResult = await guesser.send('SUBMIT_GUESS', { roomId, guess: secret1 });
    record('Correct guess advances the round', guessResult.status === 'CORRECT');
    await waitForPhase([host, guest], roomId, 2, 'WORD_SELECTION', 12000);

    stateHost = await gameState(host, roomId, host.playerId);
    drawer = stateHost.drawerId === host.playerId ? host : guest;
    drawerState = await gameState(drawer, roomId, drawer.playerId);
    await drawer.send('SELECT_WORD', { roomId, choiceId: drawerState.wordChoices[0].choiceId });
    await waitForPhase([host, guest], roomId, 2, 'DRAWING');
    drawerState = await gameState(drawer, roomId, drawer.playerId);
    const secret2 = drawerState.secretWord;
    const stroke2 = await draw(drawer, 2);
    const other2 = drawer === host ? guest : host;
    await other2.waitForBinary(2);
    const secondCanvas = await other2.send('GET_CANVAS_STATE', { round: 2 });
    record('Second-round drawing and stream recovery', (secondCanvas.payload?.events || []).some((event) => event.strokeId === stroke2));

    if (TEST_FAILOVER) {
      if (host.gatewayId !== 'gateway-1') throw new Error('Host session is not on Gateway 1; cannot exercise configured failover target.');
      const hostClosed = new Promise((resolve) => host.ws.addEventListener('close', resolve, { once: true }));
      compose('stop');
      gateway1Stopped = true;
      await Promise.race([hostClosed, sleep(10000)]);
      resumedHost = await connectToGateway('failover-resume', 'gateway-2', 32);
      resumedHost.playerId = host.playerId;
      resumedHost.roomId = roomId;
      const resumed = await resumedHost.send('RESUME_SESSION', { roomId, playerId: host.playerId, token: host.sessionToken });
      const recoveredState = await gameState(resumedHost, roomId, host.playerId);
      const recoveredCanvas = await resumedHost.send('GET_CANVAS_STATE', { round: 2 });
      record('Gateway 1 stop → Caddy reconnects on Gateway 2', resumedHost.gatewayId === 'gateway-2' && resumed.type === 'SESSION_RESUMED');
      record('Game and Canvas state resume after gateway failover', recoveredState.currentRound === 2 && (recoveredCanvas.payload?.events || []).some((event) => event.strokeId === stroke2));
      compose('start');
      gateway1Stopped = false;
    }

    const currentHost = resumedHost || host;
    const currentDrawer = drawer === host && resumedHost ? resumedHost : drawer;
    const finalGuesser = currentDrawer === guest ? currentHost : guest;
    const finalSecret = currentDrawer === guest ? secret2 : (await gameState(currentDrawer, roomId, currentDrawer.playerId)).secretWord;
    await finalGuesser.send('SUBMIT_GUESS', { roomId, guess: finalSecret });
    const untilFinished = Date.now() + 10000;
    let finished = null;
    while (Date.now() < untilFinished) {
      finished = await gameState(currentHost, roomId, currentHost.playerId).catch(() => null);
      if (finished?.status === 'FINISHED') break;
      await sleep(250);
    }
    record('Game completion after the final guess', finished?.status === 'FINISHED');
    const rematch = await currentHost.send('REMATCH', { roomId });
    record('Rematch returns the room to WAITING', rematch.type === 'ROOM_INFO' && rematch.status === 'WAITING');
  } finally {
    if (gateway1Stopped) {
      try { compose('start'); } catch (error) { console.error(`Could not restart validation Gateway 1: ${error.message}`); }
    }
    host?.close(); guest?.close(); resumedHost?.close();
  }

  const passed = results.filter((result) => result.ok).length;
  console.log(`PRODUCTION PROXY SMOKE: ${passed}/${results.length} passed`);
  if (passed !== results.length) process.exitCode = 1;
}

run().catch((error) => {
  console.error(`PRODUCTION PROXY SMOKE FAILED: ${error.message}`);
  process.exitCode = 1;
});
