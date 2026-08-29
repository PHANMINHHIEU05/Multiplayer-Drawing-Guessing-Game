# Protocol Benchmark Results (TV4: Member 04)

Generated at: 2026-08-28T14:04:09.955Z

| Mode | Total Messages | Total Payload | Avg Frame Size | Message Rate | Bandwidth | Bandwidth Reduction |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `JSON_POINT` | 1,000 | 182.0 KB | 186.4 B | 66.4 msg/s | 12.09 KB/s | Baseline (0%) |
| `JSON_BATCH` | 125 | 66.8 KB | 547.5 B | 64.9 msg/s | 34.68 KB/s | **-63.3%** |
| `BINARY_BATCH` | 130 | 7.2 KB | 56.7 B | 67.2 msg/s | 3.72 KB/s | **-96%** |
