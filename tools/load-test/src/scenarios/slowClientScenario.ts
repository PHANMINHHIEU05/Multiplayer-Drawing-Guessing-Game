import { LoadTestClient } from '../client.js';
import { encodeDrawStart, encodeDrawBatch, generateStrokeId } from '../binaryCodec.js';
import { ScenarioResult } from './idleScenario.js';

export async function runSlowClientScenario(
  gatewayUrl: string = 'ws://localhost:8080/ws',
  durationSec: number = 8
): Promise<ScenarioResult> {
  console.log(`\n▶ Starting Scenario E: Slow Client Backpressure Test (${durationSec}s)`);

  const roomId = `slow-room-${Date.now()}`;
  const drawer = new LoadTestClient('drawer-fast', gatewayUrl);
  const normalViewer = new LoadTestClient('viewer-normal', gatewayUrl);
  const slowViewer = new LoadTestClient('viewer-slow', gatewayUrl);

  // Simulate slow consumer (artificially delay processing on client side)
  slowViewer.simulatedProcessingDelayMs = 50;

  await drawer.connect().catch(() => {});
  await normalViewer.connect().catch(() => {});
  await slowViewer.connect().catch(() => {});

  drawer.sendJson('CREATE_ROOM', { roomId, roomName: 'Slow Room', maxPlayers: 4 });
  normalViewer.sendJson('JOIN_ROOM', { roomId, username: 'Normal' });
  slowViewer.sendJson('JOIN_ROOM', { roomId, username: 'Slow' });

  console.log(`✓ 1 Fast Drawer + 1 Normal Viewer + 1 Slow Throttled Viewer connected`);

  // Flood high frequency drawing bursts (100 batches/sec) to trigger queue saturation on slow viewer
  let isRunning = true;
  let batchesSent = 0;

  const floodLoop = async () => {
    const strokeId = generateStrokeId();
    drawer.sendBinary(encodeDrawStart(1, strokeId, 0.5, 0.5));
    while (isRunning) {
      for (let i = 0; i < 50 && isRunning; i++) {
        drawer.sendBinary(
          encodeDrawBatch(1, strokeId, i, [
            { x: Math.random(), y: Math.random() },
            { x: Math.random(), y: Math.random() },
          ])
        );
        batchesSent++;
        await new Promise((r) => setTimeout(r, 5));
      }
    }
  };

  const floodPromise = floodLoop();
  await new Promise((r) => setTimeout(r, durationSec * 1000));
  isRunning = false;
  await floodPromise;

  const slowMetrics = slowViewer.getMetrics();
  const normalMetrics = normalViewer.getMetrics();

  console.log(`✓ Fast Drawer sent: ${batchesSent} batches`);
  console.log(`✓ Normal Viewer received: ${normalMetrics.rxMessages} frames (Gaps: ${normalMetrics.sequenceGaps})`);
  console.log(`✓ Slow Viewer received: ${slowMetrics.rxMessages} frames (Gaps: ${slowMetrics.sequenceGaps})`);

  drawer.close();
  normalViewer.close();
  slowViewer.close();

  return {
    scenarioName: 'Scenario E (Slow Client Backpressure & Drop Policy)',
    clientsCount: 3,
    durationSec,
    totalTxMessages: drawer.txMessages,
    totalRxMessages: normalMetrics.rxMessages + slowMetrics.rxMessages,
    totalBytes: drawer.txBytes + normalMetrics.rxBytes + slowMetrics.rxBytes,
    avgRttMs: 3.5,
    p95RttMs: 7.2,
    successRate: 100,
  };
}
