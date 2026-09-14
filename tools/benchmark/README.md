# TV9 Performance Benchmark Harness

Node WebSocket load driver benchmarking the REAL authenticated stack
(JWT session, bound identity, validation + rate limits ON — never bypassed).

## Usage

```bash
# stack must be up (docker compose --profile multi-gateway up -d) and warm

# single configuration
node tools/benchmark/benchmark.mjs --protocol BINARY_BATCH --players 10 --rooms 1 --gateways 2 --runs 3

# full experiment matrix (3 protocols × 3 player counts × 2 topologies + multi-room + mixed)
node tools/benchmark/benchmark.mjs --matrix --runs 3

# increasing-load stress (stop conditions monitored manually via output)
node tools/benchmark/benchmark.mjs --stress

# mixed gameplay workload flag
node tools/benchmark/benchmark.mjs --protocol BINARY_BATCH --players 10 --gateways 2 --mixed --runs 3
```

## Options

| Flag | Default | Meaning |
|---|---|---|
| `--protocol` | BINARY_BATCH | JSON_POINT / JSON_BATCH / BINARY_BATCH |
| `--players` | 10 | total simulated players |
| `--rooms` | 1 | room count (players split evenly) |
| `--gateways` | 1 | 1 or 2 (2 = clients split 50/50 across GW1/GW2) |
| `--runs` | 3 | repetitions (median reported) |
| `--warmup` | 15 | warmup seconds (discarded) |
| `--measure` | 30 | measurement seconds |
| `--mixed` | off | add occasional guesses + low-freq chat |
| `--seed` | 1234 | deterministic workload seed |

## Workload model

Deterministic synthetic drawing (seeded PRNG): 60 points/sec per drawer
(one point per ~16.7ms), stroke = 120 points (~2s) + 300ms gap — mirrors the
production frontend batching cadence. All three protocols draw the SAME
point stream; only the transport encoding differs.

## What is measured

- RTT via APP_PING/APP_PONG (application path, 1Hz per client): avg/p50/p95/p99/jitter
  (jitter = mean absolute consecutive difference, same as frontend Inspector)
- TX/RX messages + bytes per sec; drawing bytes/point
- points/sec, batches/sec, points/batch, sequence gaps (received-side)
- errors, rate-limit hits, auth failures, disconnects
- docker stats CPU%/mem for gateway-1, gateway-2, redis, game/room/chat-service, postgres (1s sampling)

## Outputs

- `benchmark-results/raw/<ts>-<config>.json` — full raw run data
- `benchmark-results/summary/benchmark-summary-<ts>.csv` — one row per run
- `benchmark-results/summary/benchmark-aggregates-<ts>.json` — median/min/max per config

## Notes

- Clients connect through the full authenticated flow (CREATE/JOIN → JWT
  session token → RESUME-compatible binding), draw legally as the real drawer,
  guessers receive via real fanout.
- 2-Gateway runs split each room's players across both gateways so Redis
  Pub/Sub fanout is genuinely exercised.
- Docker `stats` sampling adds small overhead; keep the same method for all
  experiments (comparisons remain internally consistent).
- Benchmark traffic stays under production rate limits (60 draw batches/sec
  < 120/s limit; RTT ping 1/s).
