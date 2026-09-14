# TV6 — Cross-Gateway Control Event Synchronization: Final Report

Ngày: 2026-09-14
Phạm vi: distributed control event fanout — KHÔNG canvas recovery, KHÔNG redesign drawing pub/sub, KHÔNG message queue mới.

Tài liệu kiến trúc: [doc/control-event-fanout.md](../doc/control-event-fanout.md)

---

## A. BEFORE

**Các control events chỉ broadcast local:** `GameCommandHandler` gọi `connectionManager.broadcastToRoom[Except]` — chỉ tới WebSocket sessions đang kết nối **cùng Gateway instance** với người gửi. Bị ảnh hưởng: PLAYER_JOINED, PLAYER_LEFT, GAME_STARTED, PLAYER_GUESSED_CORRECTLY, CHAT_MESSAGE (kể cả wrong-guess chat echo), CANVAS_CLEARED (JSON path), GAME_FINISHED.

**Vì sao drawing vẫn cross-gateway được:** từ TV3, drawing binary đã có pipeline riêng — `DrawingRedisPublisher` publish envelope (originGatewayId, roomId, bytes) lên channel `drawing:room:{roomId}`; mọi Gateway subscribe pattern `drawing:room:*`, skip self-echo, broadcast bytes tới local sessions.

**Vì sao control events fail cross-gateway:** không có tầng publish tương ứng — request của người chơi ở Gateway nào thì event chỉ đến sessions của Gateway đó. Ngoài ra ROUND_STARTED/ROUND_ENDED/GAME_FINISHED do **Game Service** sinh ra theo timer server (không qua bất kỳ Gateway nào) nên trước TV6 client chỉ biết qua GET_GAME_STATE poll 5s, và DrawingRoomStateCache của Gateway khác bị stale giữa 2 lần poll.

## B. ARCHITECTURE

Đã implement (tái dùng pattern drawing Pub/Sub):

```
control event (Gateway gốc đã authorize qua gRPC)
        │
   ControlEventRouter ──① LocalRoomBroadcaster → local sessions trong room
        │
       ② ControlRedisPublisher → PUBLISH control:room:{roomId}
                                   envelope: {originGatewayId, targetRoomId,
                                              eventType, eventId, payload}
        │
      Redis ──→ Gateway khác: ControlRedisSubscriber
                 ├─ self-echo? (originGatewayId == self) → ignore
                 ├─ ROUND_STARTED → refresh DrawingRoomStateCache (drawer/round)
                 ├─ GAME_FINISHED → evict DrawingRoomStateCache
                 └─ ③ LocalRoomBroadcaster → local sessions trong room (KHÔNG republish)
```

- **Channel:** `control:room:{roomId}` — 1 channel/room (Option A, consistent với `drawing:room:{roomId}`); subscriber dùng pattern `control:room:*` (1 subscription cho tất cả room, không KEYS).
- **Game Service push:** `GameControlEventPublisher` (mới) publish ROUND_STARTED / ROUND_ENDED / GAME_FINISHED với `originGatewayId = "game-service"` → không bị self-echo suppress ở Gateway nào; chạy trên RoundScheduler executor (không phải Netty loop), blocking template chấp nhận được; failure chỉ WARN (poll 5s là safety net).
- **Envelope:** record `ControlEventEnvelope` (originGatewayId, targetRoomId, eventType, eventId, payload). Payload là client-facing JSON có sẵn — KHÔNG serialize proto.
- **Non-blocking:** toàn bộ Reactive (ReactiveStringRedisTemplate + ReactiveRedisMessageListenerContainer) — không Thread.sleep, không blocking call trên event loop.

## C. EVENT MATRIX (kết quả E2E thực tế 2 Gateway)

| Event | Scope | Redis? | GW1→GW2 | GW2→GW1 | Kết quả |
|---|---|---|---|---|---|
| PLAYER_JOINED | ROOM | YES | ✅ (CG-011d, R3a) | ✅ (CG-001/R3) | PASS |
| PLAYER_LEFT | ROOM | YES | ✅ (R4/R4b) | ✅ (CG-002) | PASS |
| Host migration | ROOM (qua ROOM state) | YES | ✅ (R5 — host mới đồng nhất) | ✅ | PASS |
| GAME_STARTED | ROOM | YES | ✅ (G3c) | — | PASS |
| ROUND_STARTED | ROOM (từ game-service) | YES | ✅ (CG-005b) | ✅ (mọi GW đều nhận) | PASS |
| ROUND_ENDED | ROOM (từ game-service) | YES | ✅ (chuyển vòng đồng bộ) | ✅ | PASS |
| GAME_FINISHED | ROOM (từ game-service) | YES | ✅ (CG-010a/b) | ✅ | PASS |
| PLAYER_GUESSED_CORRECTLY | ROOM | YES | ✅ (Q1rx, CG-006) | ✅ (CG-007a/b) | PASS |
| CHAT_MESSAGE | ROOM | YES | ✅ (CG-008) | ✅ (CG-009/b) | PASS |
| GUESS_RESULT | PRIVATE | NO | — (chỉ về submitter, Q5) | — | PASS |
| ERROR / ACK / GAME_STATE | PRIVATE | NO | — (requestId response) | — | PASS |
| Room isolation | — | — | ✅ 0 leak (CG-011a/b) | ✅ | PASS |
| Self-echo/duplicate | — | — | ✅ đúng 1 lần/client (CG-012a/b, CG-007a/b) | ✅ | PASS |

## D. SECURITY / PRIVACY (verify thực tế bằng WebSocket frame inspection)

- **GAME_STARTED không lộ secretWord**: payload broadcast là response StartGame đã bị Game Service strip secretWord (fix TV5); UI E2E UI-2 inspect mọi frame tới guesser: **0 frame** chứa secretWord. E2E G3b/G3d xác nhận cả 2 gateway.
- **PLAYER_GUESSED_CORRECTLY không chứa đáp án**: payload chỉ `{roomId, playerId, scoreAwarded}` (Q1r: `leak=false`; CG-007a/b).
- **GUESS_RESULT private**: vẫn sendToSession theo requestId — không qua Redis (Q5: 0 cross-delivery).
- **Viewer-specific state không qua Redis**: GET_GAME_STATE response chỉ về requester; secretWord chỉ tới drawer.
- **Trust boundary**: browser → WS → Gateway (authorize gRPC) → Redis. Gateway receiving tin envelope vì chỉ Gateway/game-service nội bộ publish.
- **Bonus fix**: JSON drawing path (DRAW_EVENT/DRAW_BATCH_EVENT/CLEAR_CANVAS) giờ kiểm tra drawer + round qua DrawingRoomStateCache như binary path — trước TV6 CLEAR_CANVAS JSON từ non-drawer bị broadcast (D7b — lỗ hổng tồn tại từ trước, chỉ lộ khi test cross-gateway).

## E. TEST RESULTS (chính xác, không dựng)

| Lệnh | Kết quả |
|---|---|
| `realtime-gateway: mvnw clean test` | **109/109** (100 cũ + 9 mới `ControlRedisSubscriberTest`) |
| `game-service: mvnw clean test` (Postgres+Redis docker) | **27/27** |
| `frontend: npm run lint` | 0 error |
| `frontend: npm test` | **35/35** |
| `frontend: npm run build` | OK |
| `room-service` / `chat-service` | không đổi code — kết quả TV5: 25/25, 14/14 (không re-run vì zero diff) |

Unit tests mới (`ControlRedisSubscriberTest`, 9 test): envelope round-trip, malformed drop, self-echo ignore, remote room routing + payload preservation, room isolation, game-service origin fanout, ROUND_STARTED cache refresh, GAME_FINISHED cache evict, gateway-origin không đụng cache.

## F. E2E RESULTS (2 Gateway Docker thật, 5+ client WS độc lập + 2 browser context)

| Suite | Kết quả |
|---|---|
| `tools/e2e/ws-e2e.mjs` (protocol, 81 assertion) | **81/81 PASS — 2 run liên tiếp** |
| `tools/e2e/ui-e2e.spec.js` (Playwright browser) | **2/2 PASS** |

Từ **60/65** (TV5, 5 fail do control fanout) → **81/81** (bộ mở rộng thêm 16 assertion CG-001→CG-015; 5 fail cũ toàn bộ PASS). Không còn FAIL nào. Drawing regression (CG-014: D1-D6x màu/độ rộng/eraser/clear cross-gateway) vẫn PASS — binary pub/sub không đổi.

Lưu ý vận hành phát hiện trong lúc test: `docker compose --profile multi-gateway build` phải build **cả 2 image** `realtime-gateway` và `realtime-gateway-2` (2 image riêng — nếu chỉ build cái đầu, gateway-2 chạy code cũ, mọi event một chiều sẽ fail).

## G. BUGS FOUND

| ID | Severity | Triệu chứng | Root cause | Fix |
|---|---|---|---|---|
| CG-BUG-1 | HIGH (tồn tại từ trước, lộ khi test multi-GW) | Non-drawer gửi CLEAR_CANVAS qua JSON → bị broadcast tới cả room (D7b FAIL ở run đầu TV6) | `handleClearCanvas`/`handleDrawPoint`/`handleDrawBatch` (JSON fallback path) không kiểm tra drawer/round, khác binary path | Thêm `isAuthorizedDrawer()` (DrawingRoomStateCache: PLAYING + currentDrawerId) cho cả 3 handler — [GameCommandHandler.java](../Services/realtime-gateway/src/main/java/com/drawgame/realtime_gateway/websocket/handler/GameCommandHandler.java) |
| CG-BUG-2 | MEDIUM | Remote-Gateway DrawingRoomStateCache stale giữa các vòng (drawer cũ vẫn có thể vẽ được trên Gateway khác tới khi poll 5s) | Round transition do Game Service timer, không Gateway nào biết cho tới khi client poll | `GameControlEventPublisher` publish ROUND_STARTED/GAME_FINISHED qua Redis; subscriber refresh/evict cache (CG-005c, D10, GF2 xác nhận) |
| (không phải bug hệ thống) | — | 4 FAIL giữa chừng là script artifact: race check-sau-resolve (R3a), secretWord lấy từ non-drawer (CG-007), biến `D` mất khi refactor (GF1), ROUND_STARTED mark muộn (CG-005) | test script | sửa `tools/e2e/ws-e2e.mjs` |

## H. FILES MODIFIED

**Gateway (mới):**
- `Services/realtime-gateway/.../control/ControlEventRouter.java` — local + Redis dual fanout
- `Services/realtime-gateway/.../control/LocalRoomBroadcaster.java` — local fanout wrapper
- `Services/realtime-gateway/.../control/redis/ControlEventEnvelope.java` — envelope
- `Services/realtime-gateway/.../control/redis/ControlEventCodec.java` — serialize 1 chỗ
- `Services/realtime-gateway/.../control/redis/ControlRedisPublisher.java` — reactive publish
- `Services/realtime-gateway/.../control/redis/ControlRedisSubscriber.java` — subscribe, self-echo skip, cache sync
- `Services/realtime-gateway/.../control/redis/ControlEventPayload.java` — round payload fields
- `Services/realtime-gateway/src/test/.../control/redis/ControlRedisSubscriberTest.java` — 9 unit test

**Gateway (sửa):**
- `Services/realtime-gateway/.../websocket/handler/GameCommandHandler.java` — 10 call site broadcast → `controlBroadcast()`; JSON drawing path thêm authorization (CG-BUG-1)
- `Services/realtime-gateway/src/test/.../RealtimeGatewayApplicationTests.java` — @MockBean subscriber mới

**Game Service:**
- `Services/game-service/.../service/component/GameControlEventPublisher.java` (mới) — publish ROUND_*/GAME_FINISHED
- `Services/game-service/.../service/GameCoreService.java` — wire publisher vào endRound/nextRound/finishGame

**Test/Docs:**
- `tools/e2e/ws-e2e.mjs` — +16 assertion CG-001→CG-015; sửa race/timing
- `doc/control-event-fanout.md` (mới) — kiến trúc, envelope, event matrix, trust boundary
- `docs/tv6-cross-gateway-report.md` — báo cáo này

## I. REMAINING ISSUES

**BLOCKER:** không có.

**HIGH:** không còn (HIGH-1 của TV5 — cross-gateway control events — đã giải quyết).

**MEDIUM:**
1. Redis Pub/Sub là best-effort: Gateway bỏ lỡ event (disconnect Redis ngắn) thì client dựa vào GET_GAME_STATE poll 5s để tự sửa. Chấp nhận được cho realtime UX; canvas recovery phase có thể cân nhắc Redis Stream cho replay.
2. Gateway DNS race khi restart stack (đã ghi nhận TV5, chưa sửa compose `depends_on`).

**LOW:**
1. Word pack vẫn chứa từ không dấu tiếng Việt (TV5, LOW-1 — không đổi trong task này).
2. Sai đoán thành chat message: hành vi hiện tại được giữ nguyên — sai đoán được chat-service validate & broadcast qua đường control fanout (giờ cross-gateway). Đã document.
3. `metricsStore` chưa hiển thị control-event counters (optional theo spec — counters có sẵn qua `ControlRedisSubscriber` getters, để dành cho actuator).

## J. RECONNECT READINESS

**YES** — multi-Gateway control synchronization đã ổn định, đủ điều kiện bắt đầu Reconnect + Canvas Recovery.

Bằng chứng:
- 81/81 protocol E2E × 2 run liên tiếp trên 2 Gateway Docker thật, gồm toàn bộ CG-001→CG-015 (2 chiều, room isolation, self-echo, secret safety, drawing regression).
- 109/109 gateway tests, 27/27 game-service, 35/35 frontend, 2/2 UI browser tests.
- Không còn FAIL nào; không có exception nào trong log gateway/game-service ngoài các test case chủ ý.
- ROUND_STARTED/GAME_FINISHED giờ là push events trên mọi Gateway + DrawingRoomStateCache đồng bộ tức thì — chính là tiền đề cho canvas reset/recovery khi reconnect.
- Thành phần phase sau tái dùng trực tiếp: `RESUME_SESSION`, `WebSocketClient.restoreStateAfterReconnect()`, `DrawingEventHook` (TV3-G08), `GET_CANVAS_STATE`/`SYNC_CANVAS_STATE` (tên event có sẵn 2 đầu), `doc/reconnect-canvas-recovery.md`, và giờ thêm `ControlRedisPublisher/Subscriber` + `GameControlEventPublisher` (mẫu để đẩy recovery snapshot event).
