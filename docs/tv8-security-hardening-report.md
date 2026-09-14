# TV8 — Security & Session Hardening: Final Report

Ngày: 2026-09-14
Phạm vi: secure game-session identity + WebSocket authorization + input validation + abuse protection + security regression testing. KHÔNG: accounts, login, OAuth, JWT account system, AWS, benchmark, rematch.

Tài liệu: [doc/security-session-hardening.md](../doc/security-session-hardening.md)

---

## A. BEFORE

Client tự xưng danh: `RESUME_SESSION { playerId, roomId }` — gateway tin `playerId` từ payload (chỉ verify membership). Mọi command khác (GET_GAME_STATE, SUBMIT_GUESS, SEND_CHAT, START_GAME, LEAVE_ROOM, GET_ROOM) đều nhận `playerId`/`roomId` từ payload với fallback bound-session — đổi DevTools/localStorage là hành động như người khác. Không có credential, không rate limit gateway (chỉ chat-service), không giới hạn kích thước TEXT frame, không origin validation, error message lộ internal NPE/gRPC details.

## B. THREAT MODEL (T1→T12)

Bảng đầy đủ Threat/Mitigation/Evidence: [doc/security-session-hardening.md §1](../doc/security-session-hardening.md). Tất cả 12 threat có mitigation + test pass.

## C. SECURITY ARCHITECTURE

```
Browser ──(signed JWT + payload)──► Gateway
                                     │ verify HS256 → exp → purpose → room scope
                                     │ authoritative playerId = claims.sub
                                     │ bound ConnectionContext (session→player,room)
                                     │ command authorization (matrix §6)
                                     ├──► Room/Game/Chat Service (gRPC, TRUSTED playerId — không parse JWT)
                                     └──► Redis (internal only)
```

## D. TOKEN DESIGN

HS256 (jjwt 0.12.6); claims `sub/room/purpose=GAME_SESSION/iat/exp/jti`; TTL 720' config; secret từ env `GAME_SESSION_JWT_SECRET` (≥32 chars, dev fallback WARN lớn); storage localStorage (XSS trade-off documented); issue sau CREATE/JOIN membership; verify tại RESUME + membership hiện tại; **rotate khi resume**; revocation = expiry + membership (không blacklist).

## E. COMMAND AUTHORIZATION MATRIX

Bảng đầy đủ 15 command: [doc §6](../doc/security-session-hardening.md). Điểm chính: RESUME=JWT bắt buộc; mọi command khác = bound-session identity (payload bị bỏ qua); START_GAME host-checked (room-service); DRAW/CLEAR drawer-checked (binary + JSON path); GUESS_RESULT private.

## F. INPUT VALIDATION

Nickname 1-32 (trim, no control chars) · roomName 1-64 · guess 1-128 (giữ Unicode VN) · room code `[A-Z0-9]{4,32}` · room config 2-12/1-20/15-300s · chat history limit 1-100 · TEXT frame ≤32KB (app, MESSAGE_TOO_LARGE) < 64KB (Netty) · BINARY ≤2048B + codec validation đầy đủ (giữ nguyên từ TV trước).

## G. RATE LIMITS

Token-bucket per-session in-memory: GUESS 3/s (SUBMIT_GUESS+SEND_CHAT) → `RATE_LIMITED` + retryAfterMs; CONTROL 10/s; DRAW 120/s (normal ~60/s batching unaffected — SEC-022); chat-service 5/2s (có sẵn). Heartbeat không limit. Bucket remove khi disconnect. Config qua env.

## H. MULTI-GATEWAY SECURITY

- GW1 issue → GW2 verify OK (SEC-002: SESSION_RESUMED qua GW2)
- Token rác bị GW2 reject giống GW1 (SEC-005)
- Shared secret qua docker-compose env cho cả 2 gateway
- Cross-gateway resume + canvas recovery sau auth: SEC-023 (SYNC_CANVAS_STATE 20 events)

## I. SECURITY E2E (SEC-001 → SEC-025): 32/32 PASS ×2 runs

| Case | Expected | Actual | Kết quả |
|---|---|---|---|
| SEC-001 resume same GW + rotation | SESSION_RESUMED + token mới | đúng | PASS |
| SEC-002 resume khác GW | SESSION_RESUMED | đúng | PASS |
| SEC-003 tampered sub | INVALID_SESSION_TOKEN | đúng | PASS |
| SEC-004 tampered room | rejected | đúng | PASS |
| SEC-005 sai chữ ký (GW2) | INVALID_SESSION_TOKEN | đúng | PASS |
| SEC-006 expired/tampered exp | rejected | đúng (TTL=0 trong unit test → SESSION_TOKEN_EXPIRED) | PASS |
| SEC-007 thiếu token | AUTH_REQUIRED, không fallback | đúng | PASS |
| SEC-008 token attacker + playerId victim | rejected | đúng | PASS |
| SEC-009 không token + playerId victim | rejected | đúng | PASS |
| SEC-010 victim session sống sau attack | victim chat OK, broadcast OK | đúng | PASS |
| SEC-011 legitimate newest-resume | replaced + fanout OK | đúng | PASS |
| SEC-012 non-host START_GAME | START_GAME_FAILED | đúng | PASS |
| SEC-013 non-drawer DRAW | 0 broadcast | đúng | PASS |
| SEC-014 non-drawer CLEAR (fake drawerId) | 0 event | đúng | PASS |
| SEC-015 spoofed guess playerId | bound identity dùng | sender=A | PASS |
| SEC-016 spoofed chat sender | authoritative identity+name | playerId/username đúng | PASS |
| SEC-017 room A → room B | chỉ nhận room A, token scope mismatch | đúng | PASS |
| SEC-018 guess spam | RATE_LIMITED lần 4 | đúng | PASS |
| SEC-019 chat spam | RATE_LIMITED lần 4 | đúng | PASS |
| SEC-020 oversized control (40KB) | MESSAGE_TOO_LARGE + no echo | đúng | PASS |
| SEC-021 oversized/invalid binary | 0 broadcast, không crash | đúng | PASS |
| SEC-022 normal drawing unaffected | ≈20/20 frames | đúng | PASS |
| SEC-023 auth cross-GW + canvas recovery | SYNC_CANVAS_STATE 20 events | đúng | PASS |
| SEC-024 secretWord 0 leak | 0 leak + GET_GAME_STATE rỗng | đúng | PASS |
| SEC-025 không token trong log 2 GW | sạch | đúng | PASS |
| + token claims/issuance, input validation (nickname/roomcode) | — | — | PASS |

**SEC-080 Final acceptance**: cả 2 nửa — attacker KHÔNG thành/đá được victim (SEC-008/009/010) VÀ victim P1 disconnect → reconnect GW2 bằng T1 → full restore + canvas (SEC-002 + SEC-023 + reconnect RC-017) — **PASS**.

## J. REGRESSION RESULTS

| Lệnh | Kết quả |
|---|---|
| `realtime-gateway mvnw clean test` (Redis docker) | **133/133** (128 pass + 5 skip có chủ ý) |
| `room-service mvnw clean test` | **25/25** |
| `chat-service mvnw clean test` (isolated build*) | **14/14** |
| `game-service mvnw clean test` (Postgres+Redis, isolated*) | **27/27** |
| `frontend npm run lint / test / build` | 0 lỗi / **35/35** / OK |
| `node tools/e2e/security-e2e.mjs` | **32/32 ×2 runs** |
| `node tools/e2e/ws-e2e.mjs` (regression) | **81/81 ×2 runs** |
| `node tools/e2e/reconnect-e2e.mjs` (regression) | **31/31 ×2 runs** |
| `npx playwright test` (UI) | **4/4** |

\* **Lưu ý môi trường**: Eclipse IDE đang chạy auto-build ghi đè `.class` hỏng vào `Services/chat-service/target/classes` (classpath IDE thiếu protocol dependency) → test fail với "Unresolved compilation problems" dù code đúng. Build trong thư mục isolated (/tmp) pass 14/14, game-service tương tự 27/27. **Khuyến nghị**: tắt Project → Build Automatically hoặc fix protocol classpath trong IDE. Gateway/room chạy trong repo OK vì IDE không touch các project đó giữa chừng.

## K. BUGS FOUND

| ID | Severity | Symptom | Root cause | Fix |
|---|---|---|---|---|
| SEC-BUG-1 | HIGH | SEC-017 test: GET_ROOM với payload roomId khác vẫn trả room bound → nhận ROOM_INFO thay vì lỗi | **Đây là hành vi mong muốn** (payload bị bỏ qua); test kỳ vọng sai | Sửa test: kỳ vọng = response chỉ chứa room bound, không leak room B |
| SEC-BUG-2 | MEDIUM | Frame >64KB bị Netty đóng connection im lặng (không ERROR response) | App limit (64KB) == Netty limit | Hạ app limit còn 32KB (`SECURITY_MAX_TEXT_FRAME_BYTES`) → frame 32-64KB nhận MESSAGE_TOO_LARGE sạch; >64KB vẫn bị Netty cắt (an toàn) |
| SEC-BUG-3 | MEDIUM | Error message lộ internal NPE/gRPC detail ("Cannot invoke...") | `createErrorJson` truyền raw exception message | `sanitizeErrorMessage()` — chỉ 1 dòng an toàn; signature nội bộ → "Request failed due to an internal error" |
| (env) | — | Chat/game test fail trong repo do Eclipse auto-build ghi đè target/classes | IDE classpath thiếu protocol | Isolated build pass; khuyến nghị user fix IDE |

## L. FILES MODIFIED

**Gateway (mới):** `security/GameSessionTokenService.java` (issue/verify JWT), `security/SessionRateLimiter.java` (token bucket), `security/InputValidator.java` (bounds), `src/test/.../security/GameSessionTokenServiceTest.java` (8), `src/test/.../security/SecurityComponentsTest.java` (4)
**Gateway (sửa):** `websocket/handler/GameCommandHandler.java` (bound identity mọi handler, token issue/verify/rotate, rate limit, input validation, error sanitize), `websocket/GameWebSocketHandler.java` (origin allowlist, frame size limit, limiter cleanup), `drawing/transport/DrawingWebSocketTransport.java` (binary size ceiling + draw bucket), `config/WebSocketConfig.java` (doc note), `pom.xml` (jjwt), test updates (GameCommandHandlerTest +7 security tests, dispatch/transport constructors)
**Frontend:** `store/playerStore.ts` (token storage), `websocket/WebSocketClient.ts` (token khi resume, rotation, auth-failure UX "Phiên chơi đã hết hạn", legacy migration clear), `websocket/messageHandlers.ts` (capture token từ CREATE/JOIN, clear khi LEAVE)
**Config:** `docker-compose.yml` (security env cả 2 gateway), `.env.example`
**Test/Docs:** `tools/e2e/security-e2e.mjs` (mới, 32 assertions), `tools/e2e/ws-e2e.mjs` + `reconnect-e2e.mjs` (token flow + D join sớm), `doc/security-session-hardening.md`

## M. CONFIGURATION ADDED

| Variable | Purpose | Safe example |
|---|---|---|
| `GAME_SESSION_JWT_SECRET` | JWT signing secret (shared 2 GW) | `openssl rand -base64 48` output |
| `GAME_SESSION_TOKEN_TTL_MINUTES` | Token lifetime | `720` |
| `WS_ALLOWED_ORIGINS` | WebSocket origin allowlist | `https://drawgame.example.com` |
| `SECURITY_MAX_TEXT_FRAME_BYTES` | TEXT frame limit | `32768` |
| `SECURITY_GUESS_MAX_PER_SECOND` | Guess/chat bucket | `3` |
| `security.rate-limit.control-max-per-window` / `draw-max-per-window` | Control/draw buckets | `10` / `120` |

## N. KNOWN LIMITATIONS

- Bearer token theft qua XSS: token localStorage bị đánh cắp dùng được đến expiry/rotate — mitigated (purpose hẹp, TTL, membership, newest-session, no-log, React escape); JWT không chống XSS.
- Không có user accounts: identity tự khai lúc CREATE/JOIN lần đầu; chỉ resume có proof.
- Rate limit per-Gateway in-memory — không phải distributed DDoS protection.
- Dev secret fallback (WARN) — production bắt buộc set env.
- TLS/WSS chưa deploy (frontend đã env-driven `VITE_WS_URL`, sẵn sàng wss://).
- Eclipse IDE can thiệp target/classes (env issue, không phải code).

## O. READINESS

**Security + Session Hardening đủ cho course demo? YES.**
Bằng chứng: 32/32 security E2E ×2 (kể cả acceptance scenario: attacker không mạo danh/đá được victim; victim cross-GW resume + canvas restore); 133/133 gateway tests (+19 test security mới); toàn bộ regression giữ nguyên 81/81 + 31/31 + 4/4 ×2 runs; secret 0 leak; 0 token trong log.

**Sẵn sàng cho FINAL PERFORMANCE BENCHMARK? YES.**
Cơ sở: toàn bộ nền tảng hoạt động ổn định dưới 2 gateway thật (144 E2E assertions protocol + 4 UI pass lặp lại); security layer không thêm round-trip đáng kể (JWT verify ~µs, local; rate limit in-memory O(1)); các điểm cần đo cho benchmark (drawing throughput, reconnect, recovery, control fanout) đều có E2E harness sẵn `tools/e2e/` để tái sử dụng làm load driver. Lưu ý duy nhất: rate limit DRAW 120/s có thể cần tune (env) nếu benchmark vẽ密集 hơn ~16ms/batch.
