import fs from 'fs';
import path from 'path';
import { runProtocolBenchmark } from './scenarios/benchmarkProtocol.js';
import { runIdleScenario } from './scenarios/idleScenario.js';
import { runDrawingFanoutScenario } from './scenarios/drawingFanoutScenario.js';
import { runMultiRoomScenario } from './scenarios/multiRoomScenario.js';
import { runSlowClientScenario } from './scenarios/slowClientScenario.js';

async function main() {
  const args = process.argv.slice(2);
  const isBenchmark = args.includes('--benchmark') || args.includes('-b');
  const isAll = args.includes('--all') || args.length === 0;

  const gatewayUrl = process.env.GATEWAY_URL || 'ws://localhost:8080/ws';

  console.log('╔════════════════════════════════════════════════════════╗');
  console.log('║   MULTIPLAYER DRAWING & GUESSING LOAD TEST & BENCH     ║');
  console.log('║       Member 04: Reliability, Telemetry, Performance   ║');
  console.log('╚════════════════════════════════════════════════════════╝\n');

  const benchmarkResults = await runProtocolBenchmark(gatewayUrl);

  if (isAll || args.includes('idle')) {
    await runIdleScenario(gatewayUrl, 50, 5);
  }

  if (isAll || args.includes('fanout')) {
    await runDrawingFanoutScenario(gatewayUrl, 10, 5);
  }

  if (isAll || args.includes('multiroom')) {
    await runMultiRoomScenario(gatewayUrl, 3, 4, 5);
  }

  if (isAll || args.includes('slow')) {
    await runSlowClientScenario(gatewayUrl, 5);
  }

  // Ensure results directory exists
  const resultsDir = path.join(process.cwd(), 'results');
  if (!fs.existsSync(resultsDir)) {
    fs.mkdirSync(resultsDir, { recursive: true });
  }

  // Write benchmark results to JSON and Markdown
  const jsonPath = path.join(resultsDir, 'benchmark-results.json');
  fs.writeFileSync(jsonPath, JSON.stringify(benchmarkResults, null, 2), 'utf-8');

  let mdContent = `# Protocol Benchmark Results (TV4: Member 04)\n\n`;
  mdContent += `Generated at: ${new Date().toISOString()}\n\n`;
  mdContent += `| Mode | Total Messages | Total Payload | Avg Frame Size | Message Rate | Bandwidth | Bandwidth Reduction |\n`;
  mdContent += `| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n`;

  for (const r of benchmarkResults) {
    const savings = r.bandwidthReductionPercent > 0 ? `**-${r.bandwidthReductionPercent}%**` : 'Baseline (0%)';
    mdContent += `| \`${r.mode}\` | ${r.totalMessages.toLocaleString()} | ${(r.totalBytes / 1024).toFixed(1)} KB | ${r.avgFrameSizeBytes} B | ${r.messageRatePerSec} msg/s | ${r.bandwidthKBPerSec} KB/s | ${savings} |\n`;
  }

  const mdPath = path.join(resultsDir, 'benchmark-summary.md');
  fs.writeFileSync(mdPath, mdContent, 'utf-8');

  console.log(`\n📁 Benchmark reports saved:`);
  console.log(` - ${jsonPath}`);
  console.log(` - ${mdPath}`);
  console.log('\n✅ All load tests and protocol benchmarks completed successfully!\n');
}

main().catch((err) => {
  console.error('Fatal load test error:', err);
  process.exit(1);
});
