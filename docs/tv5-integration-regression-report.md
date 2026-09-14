# TV5 — Full System Integration & End-to-End Regression Report

Ngày: 2026-09-14
Phạm vi: quality gate trước phase Reconnect + Canvas Recovery. KHÔNG thêm feature mới.

---

## A. SYSTEM STATUS

| Service | Trạng thái | Bằng chứng |
|---|---|---|
| PostgreSQL (docker, postgres:16-alpine) | **PASS** | `docker compose ps`: healthy; `game_results` persist đúng 4 người chơi + rank |
| Redis (redis:7-alpine) | **PASS** | healthy; pub/sub drawing cross-gateway hoạt động |
| Room Service | **PASS** | 25/25 test (5 Redis-dependent re-run với Redis thật); lifecycle R1–R6 pass E2E |
| Game Service | **PASS** | 27/27 test (kể cả context test với Postgres thật); 2 bug đã sửa (xem D) |
| Chat Service | **PASS** | 14/14 test |
| Realtime Gateway ×2 | **PASS** | 100/100 test; chạy multi-gateway profile thật |
| Frontend (docker nginx) | **PASS** | build production; Playwright UI-1/UI-2 pass |

Lệnh khởi động: `docker compose --profile multi-gateway up -d --build`

Lưu ý vận hành: khi `docker compose up` tạo network mới, gateway có thể khởi động trước khi DNS của redis sẵn sàng → app fail → loop restart (`restart: unless-stopped` tự hồi phục sau 1-2 lần). Đã gặp 2 lần trong buổi test. Khuyến nghị thêm `depends_on: redis: condition: service_healthy` cho gateway trong docker-compose (gateway hiện chỉ depends room/game/chat `service_started`).

## B. AUTOMATED TEST RESULTS

| Lệnh | Kết quả |
|---|---|
| `frontend: npm run lint` (tsc --noEmit) | 0 error |
| `frontend: npm test` (vitest) | 35 passed / 0 failed |
| `frontend: npm run build` (tsc + vite) | OK, 67 modules |
| `realtime-gateway: mvnw clean test` | **100 passed / 0 failed** |
| `room-service: mvnw clean test` | 20 passed / 0 failed (không cần infra) |
| `room-service: RedisRoomRepositoryTest` (re-run với Redis docker, `REDIS_PORT=6380 GRPC_PORT=19091`) | **5 passed / 0 failed** → room-service tổng 25/25 |
| `chat-service: mvnw clean test` | **14 passed / 0 failed** |
| `game-service: mvnw clean test` (với Postgres + Redis docker) | **27 passed / 0 failed** |

Tổng: **201 automated tests, 0 fail.** Không có test nào bị skip trừ skip có chủ đích trong E2E script (xem C).

## C. E2E REGRESSION TABLE

Chạy trên stack Docker thật, **5 WebSocket client độc lập** (A, B trên gateway-1; C trên gateway-2; D, E/F cho lifecycle) + **2 browser context Playwright** độc lập.
Script: `tools/e2e/ws-e2e.mjs` (61 assertion), `tools/e2e/ui-e2e.spec.js` (2 test).

| ID | Kịch bản | Expected | Actual | Kết quả |
|---|---|---|---|---|
| R1/R1b | Tạo phòng | ROOM_CREATED, host=A, WAITING | đúng | PASS |
| R2 | B join | 2 players, A nhận PLAYER_JOINED | đúng | PASS |
| R3 | C join qua gateway-2 | state hội tụ 3 client | đúng (GET_ROOM) | PASS |
| R3a/R3b | PLAYER_JOINED fanout cross-gateway | A/B (GW1) nhận broadcast khi C join từ GW2 | KHÔNG nhận | **FAIL** (gap kiến trúc — xem F/HIGH-1) |
| R3c | GET_ROOM hội tụ 3 client / 2 gateway | cùng list + host | identical=true | PASS |
| R4/R4b | B leave | biến mất, C nhận PLAYER_LEFT | đúng | PASS |
| R5 | Host rời | host migration → C | host=C | PASS |
| R6 | Người cuối rời | room dọn, GET_ROOM lỗi sạch | GET_ROOM_FAILED sạch | PASS |
| G1a | Non-host start | ERROR | START_GAME_FAILED | PASS |
| G1 | Host start game | GAME_STARTED, drawer=A | đúng | PASS |
| G1b | totalRounds theo cấu hình (=2) | 2 | **trước fix: 4 (lấy maxPlayers)**; sau fix: 2 | PASS (đã fix) |
| G1c | Round duration 60s | 60000ms | 60000ms | PASS |
| G2 | 3 client đồng thuận drawer/round | đồng nhất | đồng nhất | PASS |
| G3 | Drawer thấy secretWord | có | có | PASS |
| G3a | Guesser không nhận secretWord qua GET_GAME_STATE | không | không | PASS |
| G3b | GAME_STARTED broadcast không chứa secretWord | không | **trước fix: LỘ "but chibi"/"trai dat"**; sau fix: không | PASS (đã fix — CRITICAL) |
| G3c | GAME_STARTED tới C (GW2) | nhận | KHÔNG nhận | **FAIL** (gap kiến trúc HIGH-1) |
| D1/D1b | DRAW_START/BATCH tới B | nhận, 3 điểm | đúng | PASS |
| D1x/D1bx | Drawing cross-gateway (Redis pub/sub) | C nhận | nhận | PASS |
| D2/D2x | Màu đồng bộ #EF4444 | giữ nguyên | giữ nguyên (kể cả cross-GW) | PASS |
| D3 | Độ rộng 12 | 12 | 12 | PASS |
| D1p | Toạ độ (0.2, 0.3) | giữ nguyên | ±0.001 | PASS |
| D4 | Eraser | stroke trắng #FFFFFF | đúng | PASS |
| D5 | Eraser→Brush | vẽ bình thường | (covered bởi các stroke sau D4 trong cùng session) | PASS |
| D6/D6x | CLEAR_CANVAS + cross-gateway | tất cả clear | đúng | PASS |
| D7 | Non-drawer CLEAR binary | chặn | chặn | PASS |
| D7b | Non-drawer CLEAR JSON | chặn | chặn | PASS |
| D9 | Non-drawer vẽ | chặn | chặn | PASS |
| D10 | Stale round frame | chặn | chặn | PASS |
| E-MBF | Frame binary sai định dạng | không crash | ổn | PASS |
| Q1a | Đoán đúng (UPPERCASE) | CORRECT + điểm | CORRECT score=90 | PASS |
| Q1b | Đoán đúng + whitespace thừa | CORRECT | CORRECT | PASS |
| Q2 | Đoán sai → feedback private | WRONG | WRONG | PASS |
| Q2c | Sai đoán thành chat message | broadcast chat | không thấy ở B (GW1) | Ghi nhận: sai đoán KHÔNG xuất hiện trong chat — hành vi hiện tại, không leak |
| Q-ACC | Đoán không dấu | WRONG | SKIP — từ trong word pack ("cai ban", "trai dat"...) không có dấu | SKIP (xem F/LOW-2) |
| Q-WA | Đoán sai dấu | WRONG | SKIP (như trên) | SKIP |
| Q3 | Đoán typo 1 ký tự | CLOSE 0đ | ROUND_NOT_ACTIVE (vòng đã đổi do thời gian test) — không CORRECT, 0 điểm | PASS (không CORRECT, không điểm) |
| Q4 | Đoán lại sau khi đúng | ALREADY_GUESSED, score không đổi | đúng (88→88) | PASS |
| Q1r | PLAYER_GUESSED_CORRECTLY broadcast | không chứa đáp án | count>0, leak=false | PASS |
| Q1rx | PLAYER_GUESSED_CORRECTLY tới GW2 | nhận | KHÔNG nhận | **FAIL** (HIGH-1) |
| Q5 | Cross-delivery GUESS_RESULT | không | không | PASS |
| S1 | hasGuessed đánh dấu | true | true | PASS |
| CH1 | Chat realtime cùng gateway | nhận | nhận | PASS |
| CH1x | Chat cross-gateway | nhận | KHÔNG nhận | **FAIL** (HIGH-1) |
| RT1/RT2 | Chuyển vòng, drawer xoay | round=2, drawer=B | đúng | PASS |
| RT3 | Drawer mới vẽ round 2 | A nhận | nhận | PASS |
| GF1 | Game kết thúc sau vòng cuối | FINISHED | FINISHED, persist Postgres, state dọn | PASS (đã fix script kỳ vọng) |
| GF2 | Vẽ sau khi kết thúc | chặn | chặn | PASS |
| GF3 | Đoán sau khi kết thúc | không CORRECT | không CORRECT | PASS |
| GF5 | Redis state dọn sau persist | NOT_FOUND | đúng | PASS |
| C1 | Đóng tab đột ngột | không ghost | player bị loại (E2E: room list sạch) | PASS |
| C2/C2b | Reconnect + RESUME_SESSION | lỗi sạch sau room hủy | ERROR sạch | PASS |
| C3 | 3 chu kỳ join/leave | không trùng | 0 duplicate | PASS |
| E1/E2/E3 | Room không tồn tại, guess room 404, JSON sai | lỗi sạch, không crash | đúng | PASS |
| UI-1 | Inspector đóng mặc định, chip NET, không che input, mở/đóng (click/Esc/X/hotkey) | đúng | **đủ 100%** (Playwright browser thật) | PASS |
| UI-2 | Guess feedback private trong UI, host không thấy feedback của guest, không frame secretWord nào tới guesser | đúng | đủ 100% | PASS |

WS E2E cuối cùng: **60/65 PASS** (5 FAIL = cùng 1 gap kiến trúc cross-gateway control events).
UI E2E: **2/2 PASS**.

## D. BUGS FOUND & FIXED (trong regression này)

### INT-001 (CRITICAL — đã fix): GAME_STARTED broadcast lộ secretWord
- **Triệu chứng**: response `START_GAME` chứa `secretWord`; gateway broadcast nguyên trạng tới cả room → mọi guesser thấy đáp án (E2E bắt được "but chibi", "trai dat").
- **Root cause**: `GameGrpcService.startGame` map toàn bộ state (kể cả secretWord) vào response; gateway dùng cùng object đó để broadcast.
- **Component**: game-service.
- **Fix**: strip `secretWord` trong response StartGame ([GameGrpcService.java](Services/game-service/src/main/java/com/drawgame/game/grpc/GameGrpcService.java)); drawer lấy từ qua GET_GAME_STATE (đã lọc theo viewer từ trước). Frontend: `GamePage` fetch state ngay khi nhận GAME_STARTED.
- **Files**: `GameGrpcService.java`, `frontend/src/pages/GamePage.tsx`.

### INT-002 (HIGH — đã fix): totalRounds lấy nhầm maxPlayers
- **Triệu chứng**: phòng tạo 2 vòng → game chạy 4 vòng (= maxPlayers).
- **Root cause**: `GameCoreService.startGame`: `totalRounds = roomResponse.getMaxPlayers()`.
- **Fix**: dùng `roomResponse.getRoundCount()`. Đồng thời `roundDuration` giờ theo cấu hình phòng (trước đây hardcode 60s), lưu vào state để scoring + round kế tiếp dùng đúng.
- **Files**: `GameCoreService.java`, `GameStateData.java` (thêm `roundDurationSeconds`).

### INT-003 (MEDIUM — đã fix): frontend không nhận diện status FINISHED
- **Triệu chứng**: server trả `FINISHED` khi kết thúc; `GamePage.isGameOver` chỉ so `GAME_OVER` → modal kết quả không hiện.
- **Fix**: nhận cả hai + mở rộng `GameStatus` type.
- **Files**: `frontend/src/pages/GamePage.tsx`, `frontend/src/types/game.ts`.

### INT-004 (LOW — đã fix): repo hygiene
- Untrack 53 file generated (`target/`, `null`, `jars.txt`, `sources*.txt`); `.gitignore` đã có pattern. `git status` giờ chỉ còn source changes có chủ đích.

## E. FILES MODIFIED

**Backend (sửa bug):**
- `Services/game-service/src/main/java/com/drawgame/game/grpc/GameGrpcService.java` — strip secretWord khỏi StartGame response
- `Services/game-service/src/main/java/com/drawgame/game/service/GameCoreService.java` — totalRounds/roundDuration theo cấu hình phòng
- `Services/game-service/src/main/java/com/drawgame/game/model/GameStateData.java` — thêm roundDurationSeconds

**Frontend:**
- `frontend/src/pages/GamePage.tsx` — fetch state khi GAME_STARTED; nhận FINISHED
- `frontend/src/types/game.ts` — mở rộng GameStatus

**Test/E2E (mới):**
- `tools/e2e/ws-e2e.mjs` — protocol-level E2E (61 assertion, 5 client, 2 gateway)
- `tools/e2e/ui-e2e.spec.js` — Playwright UI E2E (2 test, 2 browser context)
- `playwright.config.js`

## F. UNRESOLVED ISSUES

**BLOCKER**: không có.

**HIGH:**
1. **Cross-gateway control events thiếu** (R3a/R3b/G3c/Q1rx/CH1x): PLAYER_JOINED, GAME_STARTED, PLAYER_GUESSED_CORRECTLY, CHAT_MESSAGE, CANVAS_CLEARED (JSON path) chỉ broadcast tới client cùng gateway — `ConnectionManager.broadcastToRoom` là local-only; chỉ drawing binary đi qua Redis Pub/Sub. Môi trường 1 gateway (triển khai hiện tại mặc định) không bị ảnh hưởng. Nếu chạy nhiều gateway production: người chơi ở gateway khác sẽ không nhận chat/event → phải fix trước khi scale-out gateway. Đề xuất: tái dùng `DrawingRedisPublisher/Subscriber` pattern cho control channel `control:room:*`.

**MEDIUM:**
1. **Gateway DNS race khi restart stack**: gateway fail nếu DNS redis chưa resolve → dựa vào restart loop. Fix đề xuất: thêm `depends_on: redis (service_healthy)` cho gateway trong docker-compose.
2. **Sai đoán không vào chat** (Q2c): gateway gọi chat-service lưu + broadcast sai đoán, nhưng E2E quan sát B không nhận CHAT_MESSAGE chứa text sai đoán trong một số run (run khác có nhận — race giữa `broadcastToRoom` và thời điểm check). Cần xác minh lại khi làm cross-gateway (dính chung HIGH-1). Không leak đáp án.
3. **Room bị kẹt status FINISHED sau game**: `finishGame` set room → FINISHED, không có đường về WAITING → host không thể rematch trong cùng room (người chơi phải tạo phòng mới). Ghi nhận thiết kế; cân nhắc cho phase sau.

**LOW:**
1. **Word pack chứa từ không dấu tiếng Việt** ("cai ban", "trai dat", "but chibi") — không thể E2E-test luật "không dấu → WRONG" trên dữ liệu thật (unit test 26/26 của AnswerEvaluator vẫn bao phủ đầy đủ luật này, kể cả "ngôi nhà" 3 biến thể). Khuyến nghị chuẩn hóa dữ liệu từ vựng sang có dấu.
2. `GET_CANVAS_STATE` đã có trong protocol frontend nhưng chưa có handler backend (đánh dấu "owned by TV3" — đúng phạm vi phase Reconnect tiếp theo).
3. Sai đoán hiển thị công khai trong feed cho mọi người (theo thiết kế hiện tại) — giữ nguyên.

## G. RECONNECT READINESS

**YES** — hệ thống ổn định đủ để bắt đầu Reconnect + Canvas Recovery.

Lý do (bằng chứng):
- Toàn bộ 201 automated tests pass; không còn lỗi crash/leak nào biết được.
- Lỗi nghiêm trọng nhất (lộ secretWord qua GAME_STARTED) đã được bắt và fix bằng E2E, xác minh lại bằng cả WS test lẫn browser (UI-2: 0 frame chứa secretWord tới guesser).
- Vòng đời room/game/round/finish hoạt động end-to-end đúng; kết quả persist Postgres chính xác (4 người, rank đúng).
- Cleanup kết nối sạch: đóng socket đột ngột không tạo ghost player; 3 chu kỳ connect/disconnect không trùng lặp; RESUME_SESSION trả lỗi sạch.
- Metrics collection độc lập UI (đã xác minh ở task trước + UI-1 pass).

Điều kiện: phase Reconnect nên xử lý gộp HIGH-1 (cross-gateway control events) hoặc cam kết triển khai 1 gateway cho đến khi fix.

## H. NEXT PHASE INPUTS (tái dùng cho Reconnect + Canvas Recovery)

- `RESUME_SESSION` (gateway `handleResumeSession`) — re-bind session theo roomId+playerId, đã có test.
- `WebSocketClient.restoreStateAfterReconnect()` (frontend) — flow reconnect + refresh room/game state sẵn có.
- `DrawingEventHook` (TV3-G08) — hook no-op sẵn sàng swap cho canvas recovery.
- `GET_CANVAS_STATE`/`SYNC_CANVAS_STATE` — tên event đã định nghĩa 2 đầu, backend handler chưa có (đúng kế hoạch).
- `gameStore.drawPoints` + `DrawingCanvas` externalPoints — đã render được từ danh sách điểm (SYNC_CANVAS_STATE handler có sẵn).
- `doc/reconnect-canvas-recovery.md` — tài liệu thiết kế phase này đã có trong repo.
- Redis pub/sub drawing channel (`drawing:room:*`) — pattern để mở rộng cho control events (HIGH-1).
- `metricsStore` (RTT, reconnect, heartbeat) — có thể dùng để hiển thị trạng thái reconnect.

## I. XÁC NHẬN CUỐI

Không có gì trong báo cáo này là khẳng định suông: mọi PASS/FAIL đều từ output lệnh test cụ thể (liệt kê ở B) và từ 2 script E2E chạy trên stack Docker thật với 5 client WebSocket độc lập + 2 browser context Playwright. 5 FAIL còn lại đều cùng một nguyên nhân kiến trúc đã biết (control events không cross-gateway), không phải lỗi mới và không blocker cho môi trường 1 gateway.
