#!/usr/bin/env node
/**
 * TV9 — generate report-ready SVG charts from benchmark aggregates JSON.
 * Usage: node tools/benchmark/charts.mjs benchmark-results/summary/benchmark-aggregates-<ts>.json
 * Output: charts/*.svg
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';

const input = process.argv[2];
if (!input) { console.error('usage: charts.mjs <aggregates.json>'); process.exit(1); }
const data = JSON.parse(readFileSync(input, 'utf8'));
mkdirSync('charts', { recursive: true });

const aggregates = data.aggregates;
const med = (cfg, key) => {
  const row = aggregates.find((a) =>
    a.configuration.protocol === cfg.protocol &&
    a.configuration.players === cfg.players &&
    a.configuration.rooms === (cfg.rooms ?? 1) &&
    a.configuration.gateways === cfg.gateways &&
    !!a.configuration.mixed === !!cfg.mixed);
  return row ? row.aggregate[key]?.median ?? null : null;
};

// ─── tiny SVG chart helper ───────────────────────────────────────────
const COLORS = { JSON_POINT: '#f59e0b', JSON_BATCH: '#3b82f6', BINARY_BATCH: '#8b5cf6' };
function barChart({ title, ylabel, series, filename, width = 760, height = 420 }) {
  // series: [{ label, value, color }]
  const margin = { top: 46, right: 20, bottom: 70, left: 70 };
  const w = width - margin.left - margin.right;
  const h = height - margin.top - margin.bottom;
  const maxV = Math.max(...series.map((s) => s.value ?? 0), 1);
  const bw = Math.min(70, (w / series.length) * 0.62);
  let bars = '', labels = '';
  series.forEach((s, i) => {
    const x = margin.left + (i + 0.5) * (w / series.length) - bw / 2;
    const bh = (s.value ?? 0) / maxV * h;
    const y = margin.top + h - bh;
    bars += `<rect x="${x}" y="${y}" width="${bw}" height="${bh}" fill="${s.color}" rx="5"/>`;
    if (s.value != null) bars += `<text x="${x + bw / 2}" y="${y - 7}" text-anchor="middle" font-size="13" font-weight="bold" fill="#0f172a">${fmt(s.value)}</text>`;
    labels += `<text x="${x + bw / 2}" y="${margin.top + h + 20}" text-anchor="middle" font-size="12" fill="#334155">${s.label}</text>`;
  });
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" font-family="system-ui,sans-serif">
  <text x="${width / 2}" y="24" text-anchor="middle" font-size="17" font-weight="bold" fill="#0f172a">${title}</text>
  <text x="16" y="${margin.top + h / 2}" text-anchor="middle" font-size="13" fill="#334155" transform="rotate(-90 16 ${margin.top + h / 2})">${ylabel}</text>
  <line x1="${margin.left}" y1="${margin.top + h}" x2="${width - margin.right}" y2="${margin.top + h}" stroke="#94a3b8"/>
  ${bars}${labels}
</svg>`;
  writeFileSync(`charts/${filename}`, svg);
  console.log(`charts/${filename}`);
}
function lineChart({ title, ylabel, series, filename, width = 760, height = 420 }) {
  // series: [{ label, color, points: [{x, y}] }]
  const margin = { top: 46, right: 20, bottom: 60, left: 70 };
  const w = width - margin.left - margin.right;
  const h = height - margin.top - margin.bottom;
  const xs = series.flatMap((s) => s.points.map((p) => p.x));
  const ys = series.flatMap((s) => s.points.map((p) => p.y ?? 0));
  const minX = Math.min(...xs), maxX = Math.max(...xs);
  const minY = 0, maxY = Math.max(...ys, 1);
  const px = (x) => margin.left + (maxX === minX ? w / 2 : (x - minX) / (maxX - minX) * w);
  const py = (y) => margin.top + h - (y - minY) / (maxY - minY) * h;
  let paths = '', dots = '', legend = '';
  series.forEach((s, si) => {
    const pts = s.points.filter((p) => p.y != null);
    if (!pts.length) return;
    const d = pts.map((p, i) => `${i ? 'L' : 'M'}${px(p.x)},${py(p.y)}`).join('');
    paths += `<path d="${d}" stroke="${s.color}" stroke-width="2.5" fill="none"/>`;
    pts.forEach((p) => { dots += `<circle cx="${px(p.x)}" cy="${py(p.y)}" r="4" fill="${s.color}"/>`; });
    legend += `<rect x="${margin.left + si * 170}" y="10" width="12" height="12" fill="${s.color}" rx="3"/><text x="${margin.left + si * 170 + 17}" y="21" font-size="12" fill="#334155">${s.label}</text>`;
  });
  // x ticks
  let xticks = '';
  [...new Set(xs)].sort((a, b) => a - b).forEach((x) => {
    xticks += `<text x="${px(x)}" y="${margin.top + h + 20}" text-anchor="middle" font-size="12" fill="#334155">${x}</text>`;
  });
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" font-family="system-ui,sans-serif">
  <text x="${width / 2}" y="40" text-anchor="middle" font-size="17" font-weight="bold" fill="#0f172a">${title}</text>
  ${legend}
  <text x="16" y="${margin.top + h / 2}" text-anchor="middle" font-size="13" fill="#334155" transform="rotate(-90 16 ${margin.top + h / 2})">${ylabel}</text>
  <line x1="${margin.left}" y1="${margin.top + h}" x2="${width - margin.right}" y2="${margin.top + h}" stroke="#94a3b8"/>
  <line x1="${margin.left}" y1="${margin.top}" x2="${margin.left}" y2="${margin.top + h}" stroke="#94a3b8"/>
  ${xticks}${paths}${dots}
</svg>`;
  writeFileSync(`charts/${filename}`, svg);
  console.log(`charts/${filename}`);
}
const fmt = (v) => (v == null ? '—' : v >= 1000 ? (v / 1000).toFixed(1) + 'k' : v >= 100 ? Math.round(v) : +v.toFixed(1));

// ─── 1-3: Protocol comparison (10 players, 1 room, 1 GW reference) ──
const protocols = ['JSON_POINT', 'JSON_BATCH', 'BINARY_BATCH'];
barChart({
  title: 'Protocol vs Messages/sec (TX) — 10 players, 1 room, 1 Gateway',
  ylabel: 'TX messages/sec',
  series: protocols.map((p) => ({ label: p.replace('_', ' '), value: med({ protocol: p, players: 10, gateways: 1 }, 'tx_msg_per_sec'), color: COLORS[p] })),
  filename: '01-protocol-messages-per-sec.svg',
});
barChart({
  title: 'Protocol vs Bandwidth (TX bytes/sec) — 10 players, 1 room, 1 Gateway',
  ylabel: 'TX bytes/sec',
  series: protocols.map((p) => ({ label: p.replace('_', ' '), value: med({ protocol: p, players: 10, gateways: 1 }, 'tx_bytes_per_sec'), color: COLORS[p] })),
  filename: '02-protocol-bandwidth.svg',
});
barChart({
  title: 'Protocol vs Bytes per Drawing Point — 10 players, 1 room, 1 Gateway',
  ylabel: 'bytes / point',
  series: protocols.map((p) => ({ label: p.replace('_', ' '), value: med({ protocol: p, players: 10, gateways: 1 }, 'bytes_per_drawing_point'), color: COLORS[p] })),
  filename: '03-protocol-bytes-per-point.svg',
});
barChart({
  title: 'Protocol vs p95 RTT — 10 players, 1 room, 1 Gateway',
  ylabel: 'p95 RTT (ms)',
  series: protocols.map((p) => ({ label: p.replace('_', ' '), value: med({ protocol: p, players: 10, gateways: 1 }, 'rtt_p95_ms'), color: COLORS[p] })),
  filename: '04-protocol-p95-rtt.svg',
});

// ─── 5-6: Load scaling (BINARY_BATCH, 1 GW) ──
lineChart({
  title: 'Players vs p95 RTT — BINARY_BATCH, 1 room, 1 Gateway',
  ylabel: 'p95 RTT (ms)',
  series: [{ label: 'p95 RTT', color: '#8b5cf6', points: [4, 10, 20].map((pl) => ({ x: pl, y: med({ protocol: 'BINARY_BATCH', players: pl, gateways: 1 }, 'rtt_p95_ms') })) }],
  filename: '05-players-p95-rtt.svg',
});
lineChart({
  title: 'Players vs Gateway CPU — BINARY_BATCH, 1 room, 1 Gateway',
  ylabel: 'Gateway CPU %',
  series: [{ label: 'gateway-1 CPU avg', color: '#0891b2', points: [4, 10, 20].map((pl) => ({ x: pl, y: med({ protocol: 'BINARY_BATCH', players: pl, gateways: 1 }, 'gateway-1_cpu_avg') })) }],
  filename: '06-players-gateway-cpu.svg',
});

// ─── 7-8: 1 GW vs 2 GW (BINARY_BATCH) ──
lineChart({
  title: '1 Gateway vs 2 Gateways — p95 RTT (BINARY_BATCH, 1 room)',
  ylabel: 'p95 RTT (ms)',
  series: [
    { label: '1 Gateway', color: '#0891b2', points: [4, 10, 20].map((pl) => ({ x: pl, y: med({ protocol: 'BINARY_BATCH', players: pl, gateways: 1 }, 'rtt_p95_ms') })) },
    { label: '2 Gateways', color: '#8b5cf6', points: [4, 10, 20].map((pl) => ({ x: pl, y: med({ protocol: 'BINARY_BATCH', players: pl, gateways: 2 }, 'rtt_p95_ms') })) },
  ],
  filename: '07-topology-p95.svg',
});
lineChart({
  title: '1 Gateway vs 2 Gateways — total Gateway CPU (BINARY_BATCH, 1 room)',
  ylabel: 'Σ gateway CPU % (avg)',
  series: [
    { label: '1 Gateway (gw1)', color: '#0891b2', points: [4, 10, 20].map((pl) => ({ x: pl, y: med({ protocol: 'BINARY_BATCH', players: pl, gateways: 1 }, 'gateway-1_cpu_avg') })) },
    { label: '2 Gateways (gw1+gw2)', color: '#8b5cf6', points: [4, 10, 20].map((pl) => {
      const a = med({ protocol: 'BINARY_BATCH', players: pl, gateways: 2 }, 'gateway-1_cpu_avg');
      const b = med({ protocol: 'BINARY_BATCH', players: pl, gateways: 2 }, 'gateway-2_cpu_avg');
      return { x: pl, y: a != null && b != null ? +(a + b).toFixed(1) : a };
    }) },
  ],
  filename: '08-topology-cpu.svg',
});

console.log('\nCharts written to charts/');
