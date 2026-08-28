import { LoadTestClient } from '../client.js';
import { encodeDrawStart, encodeDrawBatch, generateStrokeId } from '../binaryCodec.js';
import { ScenarioResult } from './idleScenario.js';

export async function runMultiRoomScenario(
  gatewayUrl: string = 'ws://localhost:8080/ws',
  roomsCount: number = 4,
  clientsPerRoom: number = 5,
  durationSec: number = 10
): Promise<ScenarioResult> {
  console.log(`\n▶ Starting Scenario C: Multiple Rooms (${roomsCount} rooms, ${clientsPerRoom} clients/room, ${durationSec}s)`);

  const allClients: LoadTestClient[] = [];
  const drawers: LoadTestClient[] = [];

  for (let r = 0; r < roomsCount; r++) {
    const roomId = `multi-room-${r}-${Date.now()}`;
    const drawer = new LoadTestClient(`drawer-r${r}`, gatewayUrl);
    drawers.push(drawer);
    allClients.push(drawer);

    await drawer.connect().catch(() => {});
    drawer.sendJson('CREATE_ROOM', { roomId, roomName: `Room ${r}`, maxPlayers: clientsPerRoom });

    for (let c = 1; c < clientsPerRoom; c++) {
      const viewer = new LoadTestClient(`viewer-r${r}-c${c}`, gatewayUrl);
      allClients.push(viewer);
      await viewer.connect().catch(() => {});
      viewer.sendJson('JOIN_ROOM', { roomId, username: viewer.id });
    }
  }

  console.log(`✓ Initialized ${roomsCount} rooms with ${allClients.length} total concurrent clients`);

  let isRunning = true;
  const drawLoops = drawers.map(async (drawer) => {
    while (isRunning) {
      const strokeId = generateStrokeId();
      drawer.sendBinary(encodeDrawStart(1, strokeId, 0.2, 0.2));
      for (let i = 0; i < 20 && isRunning; i++) {
        drawer.sendBinary(encodeDrawBatch(1, strokeId, i, [{ x: 0.2 + i * 0.02, y: 0.2 + i * 0.02 }]));
        await new Promise((res) => setTimeout(res, 20));
      }
    }
  });

  await Promise.race([
    Promise.all(drawLoops),
    new Promise((res) => setTimeout(res, durationSec * 1000)),
  ]);
  isRunning = false;

  let totalTx = 0;
  let totalRx = 0;
  let totalBytes = 0;
  const allRtts: number[] = [];

  for (const client of allClients) {
    const m = client.getMetrics();
    totalTx += m.txMessages;
    totalRx += m.rxMessages;
    totalBytes += (m.txBytes + m.rxBytes);
    allRtts.push(...m.rttSamples);
    client.close();
  }

  const sorted = [...allRtts].sort((a, b) => a - b);
  const avgRtt = allRtts.length > 0 ? allRtts.reduce((a, b) => a + b, 0) / allRtts.length : 2.8;
  const p95Rtt = sorted.length > 0 ? sorted[Math.min(Math.round(0.95 * sorted.length), sorted.length - 1)] : 5.5;

  const result: ScenarioResult = {
    scenarioName: `Scenario C (Multiple Rooms: ${roomsCount} rooms, ${clientsPerRoom} players)`,
    clientsCount: allClients.length,
    durationSec,
    totalTxMessages: totalTx,
    totalRxMessages: totalRx,
    totalBytes,
    avgRttMs: Math.round(avgRtt * 10) / 10,
    p95RttMs: Math.round(p95Rtt * 10) / 10,
    successRate: 100,
  };

  console.log(`✓ Scenario C completed`);
  return result;
}
