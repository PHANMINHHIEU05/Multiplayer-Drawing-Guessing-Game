# Security & Session Hardening (TV8)

> **Dự án:** Multiplayer Drawing & Guessing Game
> **Trạng thái:** IMPLEMENTED (TV8)
> **Phạm vi:** secure game-session identity + WebSocket authorization + input validation + abuse protection. KHÔNG: accounts/login/OAuth/JWT-access-token hệ thống đầy đủ.

## 1. Threat model

| ID | Threat | Mitigation | Test evidence |
|---|---|---|---|
| T1 | Client đổi `playerId` trong localStorage/payload | RESUME_SESSION bắt buộc JWT; mọi command khác dùng **bound session identity** (payload playerId bị bỏ qua) | SEC-007/008/015, unit `resume_AttackerTokenWithVictimPlayerId` |
| T2 | Client đụng vào room khác | Bound-room policy (GET_ROOM/GET_GAME_STATE/… chỉ dùng room đã bind); JWT có room scope | SEC-017/017b, unit `resume_TokenForOtherRoom` |
| T3 | Giả host | START_GAME xác thực host trong Room/Game Service theo bound identity | SEC-012 |
| T4 | Giả drawer | DrawingAuthorizationService dùng session-bound identity (binary + JSON path) | SEC-013/014 |
| T5 | Giả guess/chat sender | Bound identity cho SUBMIT_GUESS/SEND_CHAT; username hiển thị do chat-service resolve từ membership | SEC-015/016 |
| T6 | Sửa token (sub/room/exp/1 ký tự) | HS256 signature verify — mọi thay đổi làm chữ ký sai | SEC-003/004/005/006, unit `tamperedPayload`/`foreignSecret` |
| T7 | Dùng token hết hạn | `exp` verify → SESSION_TOKEN_EXPIRED | unit `expiredToken` (TTL=0), SEC-006 |
| T8 | Spam guess/chat | Token-bucket per-session (3/s guess+chat bucket) + chat-service limit (5/2s) | SEC-018/019 |
| T9 | Frame/message khổng lồ | App-level 32KB TEXT limit (MESSAGE_TOO_LARGE sạch) < Netty 64KB ceiling; binary ≤2048B trước decode | SEC-020/021 |
| T10 | Lộ secretWord | Đã hardening TV5-7; TV8 tái kiểm | SEC-024 (0 leak) |
| T11 | Stale connection hijack / session replacement sai | PLAYER_SESSION_REPLACED chỉ phát SAU credential+membership verify | SEC-009/010/011 |
| T12 | Cross-Gateway auth lệch | Cả 2 gateway dùng chung `GAME_SESSION_JWT_SECRET` từ env; verify độc lập | SEC-002/005 (GW2 reject giống GW1) |

## 2. Identity model

- **playerId**: logical identity (localStorage `app_player_id`) — chỉ dùng làm *reference hiển thị*, KHÔNG phải proof.
- **sessionId**: WebSocket connection identity (server-generated, đổi mỗi connect).
- **sessionToken** (mới): JWT HS256 — proof rằng connection được hành động như `sub` trong `room`.

## 3. Trust boundaries

- **UNTRUSTED**: browser, localStorage, payload JSON mọi trường (playerId/roomId/username/draw points...), binary frames.
- **TRUSTED**: Gateway sau validation; Room/Game/Chat services (nhận **trusted playerId** từ Gateway qua gRPC — không parse JWT, đúng model §28); Redis/Postgres internal.
- Rule: browser không bao giờ authoritative chỉ vì trường tồn tại trong JSON.

## 4. Token design

| Thuộc tính | Giá trị |
|---|---|
| Algorithm | HS256 (jjwt 0.12.6 — không tự viết crypto) |
| Claims | `sub` (playerId), `room`, `purpose="GAME_SESSION"`, `iat`, `exp`, `jti` |
| TTL | `GAME_SESSION_TOKEN_TTL_MINUTES` (default 720) |
| Secret | `GAME_SESSION_JWT_SECRET` (env; ≥32 ký tự; thiếu → dev fallback + WARN log lớn) |
| Storage (client) | `localStorage.app_game_session_token` — trade-off XSS đã ghi nhận |
| Issuance | Gateway, SAU CREATE_ROOM/JOIN_ROOM thành công (membership authoritative) |
| Verification | Mỗi RESUME_SESSION: signature → expiry → purpose → room scope → membership hiện tại |
| Refresh | Rotate khi resume thành công (response kèm token mới; frontend thay atomically) |
| Revocation | MVP: expiry + **membership check** (token hợp lệ nhưng đã LEAVE → PLAYER_NOT_IN_ROOM). Không blacklist |

## 5. Resume authentication flow

```
socket connect (mới) → RESUME_SESSION { roomId, playerId?, token }
  → verify signature → exp → purpose → room scope
  → payload.playerId (nếu có) phải == claims.sub (không thì reject)
  → GET_ROOM → membership hiện tại (đã LEAVE/room deleted → reject)
  → bindSession (newest-resume-wins) → PLAYER_SESSION_REPLACED (evict gateway khác)
  → SESSION_RESUMED + sessionToken mới (rotate)
```

Legacy migration: browser cũ có playerId nhưng không token → AUTH_REQUIRED → frontend clear metadata → về Home rejoin sạch (không insecure fallback).

## 6. Command authorization matrix

| Command | Requires Auth | Room Membership | Host Only | Drawer Only | Rate Limited | Private Response |
|---|---|---|---|---|---|---|
| PING/APP_PING | pre-auth | – | – | – | no | ✓ |
| CREATE_ROOM | identity-establishing | – (tạo) | – | – | control | ✓ + token |
| JOIN_ROOM | identity-establishing | – (tham gia) | – | – | control | ✓ + token |
| RESUME_SESSION | **JWT** | ✓ (verify) | – | – | control | ✓ + token mới |
| GET_ROOM | bound session | ✓ bound room | – | – | control | ✓ |
| LEAVE_ROOM | bound session | ✓ | – | – | control | ✓ |
| START_GAME | bound session | ✓ | ✓ (room-service) | – | control | ✓ |
| GET_GAME_STATE | bound session | ✓ | – | – | control | ✓ viewer-specific |
| SUBMIT_GUESS | bound session | ✓ + vòng active | – | non-drawer | **3/s** | ✓ GUESS_RESULT private |
| GET_CANVAS_STATE | bound session | ✓ + round đúng | – | – | control | ✓ |
| SEND_CHAT | bound session | ✓ | – | – | **3/s** + chat-service 5/2s | ✓ |
| GET_RECENT_CHAT | bound session | ✓ | – | – | control | ✓ |
| DRAW_POINT/BATCH/CLEAR (JSON) | bound session | ✓ | – | ✓ | draw 120/s | broadcast |
| Binary DRAW_* | bound session | ✓ | – | ✓ | draw 120/s + ≤2048B | broadcast |
| GAME_FINISHED | bound session | ✓ | – | – | control | ✓ |

## 7. Input validation (server-side, gateway)

| Input | Giới hạn |
|---|---|
| nickname | trim, 1-32 ký tự, không control chars |
| roomName | trim, 1-64, không control chars |
| guess | trim, 1-128, không control chars (giữ Unicode tiếng Việt; matching không đổi) |
| room code (join) | 4-32 ký tự `[A-Z0-9]` |
| room config | maxPlayers 2-12, totalRounds 1-20, roundDuration 15-300s |
| chat limit (GET_RECENT_CHAT) | 1-100 |
| TEXT frame | ≤ 32KB (`SECURITY_MAX_TEXT_FRAME_BYTES`) → MESSAGE_TOO_LARGE |
| BINARY frame | ≤ 2048B trước decode; codec validate version/opcode/round/points≤256/coords 0-1/RGB/width 1-64 |

## 8. Rate limits

| Bucket | Limit (default) | Scope | On exceed |
|---|---|---|---|
| GUESS (SUBMIT_GUESS, SEND_CHAT) | 3 / giây / session | per-session in-memory (Gateway-local) | `RATE_LIMITED` + `retryAfterMs:500`; không disconnect |
| CONTROL (mọi command khác) | 10 / giây / session | per-session in-memory | `RATE_LIMITED` |
| DRAW (binary + JSON drawing) | 120 / giây / session | per-session in-memory | silently drop frame; connection sống |
| Chat (chat-service) | 5 / 2s / player (có sẵn TV trước) | Redis | CHAT_RATE_LIMITED |

Heartbeat (APP_PING 2s) không bị limit. Normal drawing ~60-65 batches/s < 120 → SEC-022 verified không throttle. Buckets bị remove khi disconnect (không leak). Trade-off documented: per-Gateway in-memory đủ chống 1 client spam; abuser qua nhiều socket nhiều gateway cần infra-level DDoS (ngoài scope).

## 9. Origin validation

- WebSocket handshake Origin check (`WS_ALLOWED_ORIGINS`, comma-separated) **ở WebSocket layer** (không nhầm REST CORS).
- Dev (allowlist rỗng): cho phép localhost/127.0.0.1 + non-browser client (không Origin header — E2E tests).
- Prod: PHẢI set allowlist; có allowlist thì từ chối connection không Origin.
- REST CORS hiện có (`/ws`) giữ nguyên cho HTTP probes.

## 10. Error sanitization & logging

- `createErrorJson` sanitize: chỉ 1 dòng message an toàn; signature NPE/class-name/stack → "Request failed due to an internal error".
- Error codes ổn định: AUTH_REQUIRED, INVALID_SESSION_TOKEN, SESSION_TOKEN_EXPIRED, ROOM_SCOPE_MISMATCH, PLAYER_NOT_IN_ROOM, INVALID_ROOM_CODE, INVALID_NICKNAME, INVALID_GUESS, RATE_LIMITED, MESSAGE_TOO_LARGE, INVALID_SESSION...
- Log: KHÔNG log token đầy đủ (chỉ jti), KHÔNG log secretWord; SEC-025 verify 0 token trong 2000 dòng log cuối của cả 2 gateway.

## 11. Secret-data rules

- secretWord: chỉ trong GAME_STATE response cho **drawer** (viewer-specific, đã verify TV5-7); SEC-024 re-verify 0 leak mọi frame tới guesser.
- JWT signing secret: chỉ backend env; không frontend; không log; không commit giá trị thật.
- Không store secretWord/aliases/score/chat/drawing trong token.

## 12. Multi-Gateway behavior

- Cả gateway dùng chung `GAME_SESSION_JWT_SECRET` (docker-compose env) → token issue ở GW1 verify được ở GW2 (SEC-002) và token rác bị reject giống nhau (SEC-005).
- PLAYER_SESSION_REPLACED (control event) chỉ phát sau verify — không thể dùng để đá victim (SEC-010).

## 13. Known limitations

- **Bearer token theft qua XSS**: token trong localStorage bị đánh cắp thì dùng được đến khi hết hạn/rotate — không thể chống bằng JWT; mitigations: purpose hẹp, TTL, membership check, newest-session, không log token, React escape-by-default (không dangerouslySetInnerHTML).
- Không có user accounts — identity vẫn là self-asserted lúc CREATE/JOIN (lần đầu), chỉ *resume* là có proof.
- Rate limit per-Gateway in-memory — không phải distributed anti-DDoS.
- Dev secret fallback tồn tại (WARN lớn) — production PHẢI set env.
- TLS/WSS: chưa deploy trong task này; frontend đã dùng `VITE_WS_URL` env (không hardcode ws:// trong app code) — production set `wss://…`.

## 14. Deployment environment requirements

```
GAME_SESSION_JWT_SECRET=<openssl rand -base64 48>   # BẮT BUỘC production, ≥32 chars
GAME_SESSION_TOKEN_TTL_MINUTES=720
WS_ALLOWED_ORIGINS=https://your-frontend.example.com
SECURITY_MAX_TEXT_FRAME_BYTES=32768
# rate limits (optional overrides)
SECURITY_GUESS_MAX_PER_SECOND=3
```

## 15. Verification (TV8)

- Security E2E `tools/e2e/security-e2e.mjs`: **32/32 PASS ×2 runs** (SEC-001→025 + input validation).
- Regression: ws-e2e **81/81 ×2**, reconnect-e2e **31/31 ×2**, UI Playwright **4/4**.
- Gateway unit/integration: **133/133** (thêm GameSessionTokenServiceTest 8, SecurityComponentsTest 4, handler security tests 7).
- room-service 25/25, chat-service 14/14, game-service 27/27 (isolated build — xem báo cáo TV8 về Eclipse interference), frontend 35/35.
