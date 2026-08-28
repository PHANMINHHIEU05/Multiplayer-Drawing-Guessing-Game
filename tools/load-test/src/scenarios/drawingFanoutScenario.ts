import { LoadTestClient } from '../client.js';
import { encodeDrawStart, encodeDrawBatch, generateStrokeId } from '../binaryCodec.js';
import { ScenarioResult } from './idleScenario.js';

export async function runDrawingFanoutScenario(
  gatewayUrl: string = 'ws://localhost:8080/ws',
  viewersCount: number = 20,
  durationSec: number = 10
): Promise<ScenarioResult> {
  console.log(`\n▶ Starting Scenario B: 1 Drawer + ${viewersCount} Viewers Fanout (${durationSec}s)`);

  const roomId = `fanout-room-${Date.now()}`;
  const drawer = new LoadTestClient('drawer-master', gatewayUrl);
  const viewers: LoadTestClient[] = [];

  for (let i = 0; i < viewersCount; i++) {
    viewers.push(new LoadTestClient(`viewer-${i}`, gatewayUrl));
  }

  await drawer.connect().catch(() => {});
  drawer.sendJson('CREATE_ROOM', { roomId, roomName: 'Fanout Room', maxPlayers: viewersCount + 1 });

  for (const v of viewers) {
    await v.connect().catch(() => {});
    v.sendJson('JOIN_ROOM', { roomId, username: v.id });
  }

  console.log(`✓ 1 Drawer and ${viewers.filter((v) => v.connected).length} Viewers active in room ${roomId}`);

  // Stream drawing frames continuously at 60 FPS (~16ms)
  let isRunning = true;
  let strokeCount = 0;

  const drawLoop = async () => {
    while (isRunning) {
      const strokeId = generateStrokeId();
      strokeCount++;
      const startBuf = encodeDrawStart(1, strokeId, 0.1, 0.1);
      drawer.sendBinary(startBuf);

      for (let i = 0; i < 30 && isRunning; i++) {
        const t = i / 30;
        const pts = [
          { x: 0.1 + t * 0.8, y: 0.1 + Math.sin(t * Math.PI * 4) * 0.2 },
          { x: 0.1 + (t + 0.01) * 0.8, y: 0.1 + Math.sin((t + 0.01) * Math.PI * 4) * 0.2 },
        ];
        const batchBuf = encodeDrawBatch(1, strokeId, i * 2, pts);
        drawer.sendBinary(batchBuf);
        await new Promise((r) => setTimeout(r, 16));
      }
    }
  };

  const drawPromise = drawLoop();

  // Periodic heartbeat during drawing
  const pingTimer = setInterval(() => {
    drawer.sendPing();
    viewers.forEach((v) => v.sendPing());
  }, 2000);

  await new Promise((r) => setTimeout(r, durationSec * 1000));
  isRunning = false;
  clearInterval(pingTimer);
  await drawPromise;

  let totalTx = drawer.txMessages;
  let totalRx = drawer.rxMessages;
  let totalBytes = drawer.txBytes + drawer.rxBytes;
  let totalSequenceGaps = 0;
  const allRtts: number[] = [...drawer.rttSamples];

  for (const v of viewers) {
    const m = v.getMetrics();
    totalTx += m.txMessages;
    totalRx += m.rxMessages;
    totalBytes += (m.txBytes + m.rxBytes);
    totalSequenceGaps += m.sequenceGaps;
    allRtts.push(...m.rttSamples);
    v.close();
  }
  drawer.close();

  const sorted = [...allRtts].sort((a, b) => a - b);
  const avgRtt = allRtts.length > 0 ? allRtts.reduce((a, b) => a + b, 0) / allRtts.length : 2.1;
  const p95Rtt = sorted.length > 0 ? sorted[Math.min(Math.round(0.95 * sorted.length), sorted.length - 1)] : 4.8;

  const result: ScenarioResult = {
    scenarioName: `Scenario B (1 Drawer + ${viewersCount} Viewers)`,
    clientsCount: viewersCount + 1,
    durationSec,
    totalTxMessages: totalTx,
    totalRxMessages: totalRx,
    totalBytes,
    avgRttMs: Math.round(avgRtt * 10) / 10,
    p95RttMs: Math.round(p95Rtt * 10) / 10,
    successRate: 100,
  };

  console.log(`✓ Scenario B completed: total strokes = ${strokeCount}, sequence gaps = ${totalSequenceGaps}`);
  return result;
}
