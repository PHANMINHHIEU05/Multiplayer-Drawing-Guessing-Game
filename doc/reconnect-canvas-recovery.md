# Reconnect & Canvas Recovery — Architecture / Contract Preparation

> **Project:** Multiplayer Drawing & Guessing Game  
> **Status:** Preparation contract for Phase 2 (TV2 / TV3)  
> **Canonical document:** This file defines the reconnect and canvas-recovery extension. The existing live drawing wire format remains defined by [`drawing-protocol.md`](drawing-protocol.md).

## 1. Scope and non-goals

This document fixes the contracts for:

- logical player identity versus a WebSocket connection identity;
- reconnect and room/game restoration;
- current-round canvas recovery;
- recovery ordering and the live-event race;
- round/game/room cleanup and stale drawing protection;
- the handoff between TV1, TV2, and TV3.

The current fix implements the minimal `RESUME_SESSION` bind path. This task does **not** implement the reconnect UX, Redis recovery repository, snapshot engine, JWT authentication, or a complete drawing refactor. The remaining contracts below are the target for Phase 2 implementation.

## 2. Existing system inspected

### 2.1 Frontend

| Area | Current behavior | Consequence |
| --- | --- | --- |
| Player identity | `frontend/src/store/playerStore.ts` creates `player_<random>` once and persists it as `app_player_id` in `localStorage`. Username is persisted separately as `app_username`. | The persisted player ID can survive a new WebSocket connection. It is an MVP identity, not authentication. |
| WebSocket connection | `WebSocketClient` creates a new browser `WebSocket` after disconnect. It uses exponential backoff from 1s to a maximum of 10s with ±20% jitter, application `APP_PING`/`APP_PONG`, and rejects pending requests when a socket closes. | `sessionId` must be treated as connection-scoped and requests in flight are not resumable. |
| Current reconnect | `restoreStateAfterReconnect()` now sends `RESUME_SESSION` with the remembered room/player, then refreshes `GET_ROOM` and `GET_GAME_STATE` for a playing room. | The minimal session re-bind path is implemented; canvas recovery and reconnect UX are still pending. |
| Room/game stores | `roomStore` and `gameStore` are in-memory stores. `gameStore` holds `gameState`, current drawer, secret word, and a flat `drawPoints` array. | A page reload loses the remembered room; a socket reconnect keeps process memory but still needs authoritative refresh. |
| Canvas | `DrawingCanvas` uses normalized coordinates and re-renders from `externalPoints`. `ROUND_STARTED` and `CANVAS_CLEARED` clear local points. | A replay response can be applied without changing the binary drawing layout. |
| Drawing client modes | JSON point/batch and binary batch are both sent. Binary frames carry round, stroke ID, and per-stroke sequence; current JSON frames do not carry all of those fields. | Phase 2 must normalize both accepted input paths before recording recovery history. |

### 2.2 Realtime Gateway

| Area | Current behavior | Consequence |
| --- | --- | --- |
| WebSocket session | `GameWebSocketHandler` uses `WebSocketSession.getId()` as the session ID and calls `ConnectionManager.remove()` on disconnect. | The current mapping is ephemeral; there is no disconnect grace period. |
| Session mapping | `ConnectionManager` keeps `sessionId -> roomId` and `sessionId -> playerId` in local `ConcurrentHashMap`s. `bindSession()` is called after `CREATE_ROOM` or `JOIN_ROOM`. | A new Gateway instance has no old mapping. A logical resume must bind the new session after membership verification. |
| Room/game commands | `GET_ROOM` and `GET_GAME_STATE` already exist and fall back to the bound room/player when fields are absent. `RESUME_SESSION` now verifies membership through `GET_ROOM` and binds the new session. The handler still accepts client-supplied IDs for other commands. | Reuse these commands; Phase 2 must make the bound context authoritative after resume. |
| Drawing binary path | `DrawingWebSocketTransport` decodes to `DrawingMessage`; `DrawingAuthorizationService` checks bound room/player, cached playing state, current drawer, and round. `DrawingMessageRouter` broadcasts locally and publishes to Redis. | This is the intended authorization boundary and must also own recovery recording. |
| Drawing JSON path | `GameCommandHandler.handleDrawPoint/handleDrawBatch/handleClearCanvas` directly broadcasts using payload `roomId`/`drawerId`; it does not use the binary authorization router, does not record history, and does not validate round. | This is an implementation gap. TV3 must not treat this path as compliant until it is routed through the same authorization and recovery boundary. |
| Drawing state cache | `DrawingRoomStateCache` is local memory only. It is updated on `GAME_STARTED` and `GET_GAME_STATE` when status is `PLAYING`; there is no push handling for round transitions. | It is a fast-path projection, not authoritative state or shared recovery storage. A cache miss must be repaired from Game Service. |
| Redis drawing | `DrawingRedisPublisher` publishes raw encoded bytes to `drawing:room:{roomId}`. `DrawingRedisSubscriber` only fans out Pub/Sub messages and suppresses self-echo. Chat already uses Redis Streams with bounded length and TTL, but drawing does not yet have a recovery stream. | Pub/Sub is live fanout only; it cannot recover events for an offline client. Reuse the existing Redis Streams approach for the new drawing recovery store, without treating the Pub/Sub channel as history. |

### 2.3 Room Service and Game Service

- Room membership is keyed by `playerId`, not `sessionId`, in Redis hashes `room:{roomId}:players` and an ordered player list. `LEAVE_ROOM` removes membership; an unexpected WebSocket disconnect does not.
- Room Service has `WAITING`, `PLAYING`, and `FINISHED` states. The room TTL is configured as `room.ttl: 7200` seconds. When the last player leaves, the room keys are deleted. There is no reconnect grace period or room-deletion notification to the Gateway.
- `GET_ROOM` returns membership and host information but does not take a player ID and does not itself verify membership.
- Game state is ephemeral Redis state under `game:{roomId}:state`, scores under `game:{roomId}:scores`, and guessed players under `game:{roomId}:guessed`. PostgreSQL is used for persisted game results, not drawing points.
- `GET_GAME_STATE` hides `secretWord` unless the supplied viewer ID equals the drawer ID. The current Gateway does not independently verify that viewer ID against a Room Service membership before calling it.
- Game Service is authoritative for current round, drawer, timer, and scores. Its current implementation uses a hard-coded 60-second round duration and has a `totalRounds` initialization that uses `maxPlayers`; these are existing lifecycle defects to resolve separately.
- Round transitions are performed by the Game Service scheduler. The Gateway currently does not receive authoritative `ROUND_STARTED`/`ROUND_ENDED` push events; a later `GET_GAME_STATE` refresh is the only reliable repair path.

## 3. Identity and session model

The two identities are deliberately different:

```text
logical player:   playerId = player_abc123       (stable for the MVP)
connection:       sessionId = ws-session-001     (one WebSocket only)

disconnect

same player:      playerId = player_abc123
new connection:   sessionId = ws-session-999
```

### 3.1 MVP identity source

- `playerId` is supplied by the client from persisted `localStorage` during `RESUME_SESSION` and is verified against Room Service membership.
- `username` is display data only. It is never authoritative identity.
- `sessionId` is generated by the WebSocket server and changes on every new connection.
- The MVP does not invent or simulate JWT. The future authoritative source is `JWT subject` / authenticated principal. The resume and canvas contracts must remain unchanged when that identity source is replaced.

### 3.2 Conceptual connection context

The Gateway must expose an equivalent of this context, whether or not it introduces a new Java class:

```text
ConnectionContext {
  sessionId: string          // WebSocket connection identity
  playerId: string | null   // logical identity after resume/join
  roomId: string | null     // bound room after resume/join
  connectedAt: number       // server epoch milliseconds
  state: NEW | ACTIVE | RECOVERING | CLOSED
  gatewayId: string
}
```

The context is not Room Service membership. Disconnect removes the local connection context but does **not** call `LEAVE_ROOM`; membership remains available for resume until the room itself expires, is deleted, or the player explicitly leaves.

For stale-session protection in a multi-Gateway deployment, TV3 should maintain an ephemeral Redis binding:

```text
session:room:{roomId}:player:{playerId}
  -> {gatewayId}|{sessionId}
  TTL -> configurable session lease
```

The latest successful resume wins. A session that no longer matches this binding must not draw or request a recovery for that player. Disconnect cleanup must be compare-and-delete so an old session cannot delete a newer binding. This key is a lease, not player identity and not room membership.

## 4. Reconnect and room/game restore contract

### 4.1 Canonical flow

```text
socket closes
  -> client keeps playerId and last roomId in memory/localStorage
  -> reconnect with existing bounded backoff
  -> WebSocket opens with a new sessionId
  -> RESUME_SESSION { playerId, roomId }
  -> Gateway loads Room Service GET_ROOM and verifies player membership
  -> Gateway binds the new session context
  -> SESSION_RESUMED acknowledgement
  -> GET_ROOM using the bound room context
  -> GET_GAME_STATE using the bound player context
  -> if game status is PLAYING: GET_CANVAS_STATE { round }
  -> apply recovery, then accept normal live drawing
```

`RESUME_SESSION` does **not** call `JOIN_ROOM`. It does not add a player, update the username, migrate the host, or change Room Service membership. It only verifies an existing membership and binds a new WebSocket session.

After a successful resume, `GET_ROOM` and `GET_GAME_STATE` must use the server-bound `roomId` and `playerId`. Any conflicting client-supplied value is ignored or rejected; it must not override the bound context.

### 4.2 New command: `RESUME_SESSION` (minimal bind path implemented)

Request envelope:

```json
{
  "type": "RESUME_SESSION",
  "requestId": "req-resume-001",
  "payload": {
    "playerId": "player_abc123",
    "roomId": "VM5CZD"
  }
}
```

Rules:

1. `playerId` and `roomId` are required non-blank strings.
2. Gateway calls Room Service `GET_ROOM(roomId)` and checks that one returned player has exactly `playerId`.
3. Gateway binds `sessionId -> playerId, roomId` only after that check succeeds.
4. Gateway must not use `username` as identity and must not accept a client-provided `sessionId`.

Response envelope:

```json
{
  "type": "SESSION_RESUMED",
  "requestId": "req-resume-001",
  "payload": {
    "playerId": "player_abc123",
    "roomId": "VM5CZD",
    "roomStatus": "PLAYING"
  }
}
```

The response is intentionally small. It does not duplicate room, game, score, timer, secret word, or canvas state.

### 4.3 Reused commands

No `RESTORE_ROOM`, `RESTORE_GAME`, `RESTORE_SCORE`, or `RESTORE_TIMER` command is added.

After `SESSION_RESUMED`, TV2 reuses:

```json
{ "type": "GET_ROOM", "requestId": "req-room-001", "payload": {} }
{ "type": "GET_GAME_STATE", "requestId": "req-game-001", "payload": {} }
```

The responses remain the existing `ROOM_INFO` and `GAME_STATE` shapes. `GET_GAME_STATE` remains viewer-specific: only the drawer may receive `secretWord`; canvas recovery never carries it.

### 4.4 Resume outcomes

| Outcome | Gateway response | TV2 action |
| --- | --- | --- |
| Room key is missing | `ERROR` / `ROOM_NOT_FOUND` | Clear room/game/canvas state and return Home. Stop retrying this room. |
| Room is explicitly closed/finished | `ERROR` / `ROOM_ALREADY_CLOSED` | Clear active game/canvas. If membership is still returned, show the room’s lobby/finished state; otherwise Home. |
| Player is not in returned membership | `ERROR` / `PLAYER_NOT_IN_ROOM` | Clear room/game/canvas and return Home. Do not call `JOIN_ROOM` automatically. |
| Request has missing/malformed identity | `ERROR` / `INVALID_SESSION` | Stop the resume attempt and return Home/login identity setup. |
| Room is present and status is `WAITING` | `SESSION_RESUMED` then `ROOM_INFO` | Render Lobby; do not call `GET_GAME_STATE`. |
| Room is present and status is `PLAYING`, game state exists | `SESSION_RESUMED`, `ROOM_INFO`, `GAME_STATE` | Render Game and request canvas recovery for `currentRound`. |
| Game state is absent while room says `PLAYING` | `ERROR` / `GAME_NOT_ACTIVE` after bounded retry | Clear active canvas and surface a recoverable lifecycle error; TV1 must reconcile the room/game status mismatch. |
| Temporary Gateway/Room/Game failure | `ERROR` / `RESUME_RETRYABLE` | Keep room context, show reconnecting state, and retry with bounded backoff. |

The current implementation emits generic wrappers such as `ROOM_JOIN_FAILED`, `GET_ROOM_FAILED`, and `GET_GAME_STATE_FAILED`. Phase 2 must map those transport/service failures to the stable contract codes above; this document does not claim that mapping is already implemented.

## 5. Canvas recovery strategy

### 5.1 Decision: Option A — bounded current-round event replay

The MVP chooses **bounded current-round event replay**, implemented with a Redis Stream. It does not create a PNG snapshot or persist drawing points in PostgreSQL.

Reasoning from the inspected code:

- there is no snapshot infrastructure today;
- the current frontend already uses normalized semantic drawing data;
- the current Game Service round is hard-coded to 60 seconds;
- the client batches at approximately 16ms / 60Hz, which is roughly 3,750 flushes during a continuously active 60-second round before overhead;
- a configurable stream bound of 8,192 events gives practical headroom for the current round without concluding a benchmark result;
- semantic replay is independent of the reconnecting canvas dimensions and avoids changing the binary wire format.

If future round durations or event rates exceed this bound, TV3 must tune configuration or introduce snapshot-plus-delta as a later optimization. It must not silently make the history unbounded.

### 5.2 Redis structure

Canonical current-round stream key:

```text
drawing:room:{roomId}:round:{round}:events
```

Recommended configuration names and initial values:

```text
gateway.drawing.recovery.max-events-per-round = 8192
gateway.drawing.recovery.ttl-seconds = 300
gateway.drawing.recovery.session-lease-seconds = configurable
```

The 8,192 value is derived from the current 60-second/60Hz batching shape plus headroom, not from a performance claim. The 300-second TTL is a safety net for orphaned ephemeral data; normal lifecycle cleanup must delete the stream earlier. If the configured round duration plus reconnect grace exceeds the TTL, the TTL must be increased.

TV3 must use `XADD` with a bounded maximum length (and/or an explicit byte guard), `XRANGE`/`XREAD`, and direct key deletion. It must not run Redis `KEYS` at runtime.

Because cleanup must not depend on wildcard scans, maintain a small per-room recovery-key index:

```text
drawing:room:{roomId}:recovery-keys
  set members -> exact current-round stream keys
```

The index and each stream receive the same safety TTL. On game/room cleanup, TV3 reads the index, deletes the exact members, then deletes the index.

### 5.3 Recovery event shape

The existing binary frames remain unchanged. Recovery is a JSON response containing transport-neutral normalized drawing events. Each Redis Stream entry is one accepted logical drawing operation and receives a Redis Stream ID; that ID is the authoritative recovery cursor.

```json
{
  "streamId": "1735600000123-0",
  "round": 2,
  "type": "DRAW_START",
  "strokeId": "550e8400-e29b-41d4-a716-446655440000",
  "point": {
    "x": 0.10,
    "y": 0.20,
    "color": "#000000",
    "size": 4,
    "isNewPath": true
  }
}
```

```json
{
  "streamId": "1735600000139-0",
  "round": 2,
  "type": "DRAW_BATCH",
  "strokeId": "550e8400-e29b-41d4-a716-446655440000",
  "seqStart": 0,
  "points": [
    { "x": 0.11, "y": 0.21, "color": "#000000", "size": 4, "isNewPath": false },
    { "x": 0.12, "y": 0.22, "color": "#000000", "size": 4, "isNewPath": false }
  ]
}
```

```json
{
  "streamId": "1735600000200-0",
  "round": 2,
  "type": "DRAW_END",
  "strokeId": "550e8400-e29b-41d4-a716-446655440000"
}
```

```json
{
  "streamId": "1735600000250-0",
  "round": 2,
  "type": "CLEAR_CANVAS"
}
```

For binary input, TV3 carries the `strokeId`, `seqStart`, and style from `DRAW_START` into normalized recovery entries. For legacy JSON input, TV3 must generate a server-side stroke ID at the normalized boundary and preserve the full point style. A client timestamp is telemetry only and is never used for ordering.

### 5.4 `CLEAR_CANVAS` compaction

`CLEAR_CANVAS` is a recovery boundary, not an ordinary point. On an accepted clear, TV3 should atomically delete the old current-round stream and create a new stream containing one `CLEAR_CANVAS` entry before accepting subsequent drawing events. Therefore:

```text
DRAW A, DRAW B, CLEAR, DRAW C
recovery stream -> CLEAR, DRAW C
```

This saves bandwidth and guarantees that replay cannot restore pre-clear data. The operation must be serialized with event recording so a concurrent recovery sees either the old stream before the clear or the new generation after it, never a partially compacted stream.

## 6. Recovery commands and response

### 6.1 New command: `GET_CANVAS_STATE` (contract only)

The request uses the same JSON envelope as existing commands. `roomId` and `playerId` are not sent because they come from the bound connection context.

```json
{
  "type": "GET_CANVAS_STATE",
  "requestId": "req-canvas-001",
  "payload": {
    "round": 2
  }
}
```

Validation:

- the session must be resumed and bound to a room/player;
- the requested round must equal the authoritative current round;
- the room must have an active `PLAYING` game;
- the requester must be a current room member;
- stale, malformed, or cross-room requests are rejected and do not read/write recovery state.

`GET_CANVAS_STATE` is present as a frontend protocol constant so TV2 and TV3 share the
same name. The realtime-gateway handler and Redis recovery implementation are intentionally
not part of this preparation task.

### 6.2 Reused response type: `SYNC_CANVAS_STATE`

`SYNC_CANVAS_STATE` already exists in the frontend message-type list and is reused as the canonical recovery response; `CANVAS_STATE` is not added as a duplicate response name.

```json
{
  "type": "SYNC_CANVAS_STATE",
  "requestId": "req-canvas-001",
  "payload": {
    "roomId": "VM5CZD",
    "round": 2,
    "mode": "EVENT_REPLAY",
    "historyComplete": true,
    "lastStreamId": "1735600000250-0",
    "events": [
      {
        "streamId": "1735600000250-0",
        "round": 2,
        "type": "CLEAR_CANVAS"
      },
      {
        "streamId": "1735600000260-0",
        "round": 2,
        "type": "DRAW_START",
        "strokeId": "550e8400-e29b-41d4-a716-446655440000",
        "point": {
          "x": 0.30,
          "y": 0.40,
          "color": "#000000",
          "size": 4,
          "isNewPath": true
        }
      }
    ]
  }
}
```

`events` contains drawing data only. It must never contain `secretWord`, answer aliases, scores, timer fields, or Game Service internals. If bounded trimming removed the beginning of the current round, `historyComplete` is `false`; TV2 must not claim an exact restore and should show the recoverable-state warning defined by its UX.

### 6.3 Error envelope and stable codes

All errors use the existing envelope shape:

```json
{
  "type": "ERROR",
  "requestId": "req-canvas-001",
  "code": "WRONG_ROUND",
  "message": "Requested round is not the active round",
  "error": {
    "code": "WRONG_ROUND",
    "message": "Requested round is not the active round"
  }
}
```

Stable contract codes:

| Code | Meaning | Client behavior |
| --- | --- | --- |
| `INVALID_SESSION` | Missing or malformed resume identity, or a session no longer bound. | Stop this resume/recovery attempt. |
| `ROOM_NOT_FOUND` | Room key does not exist. | Clear state and Home. |
| `PLAYER_NOT_IN_ROOM` | `playerId` is not in the Room Service membership list. | Clear state and Home. |
| `ROOM_ALREADY_CLOSED` | Room is explicitly closed/finished for resume. | Do not restore active canvas. |
| `GAME_ALREADY_FINISHED` | Game was known to have finished. | Clear active canvas; show finish/lobby state if room remains. |
| `GAME_NOT_ACTIVE` | No active game state is available. | Do not request canvas replay; bounded retry then surface lifecycle error. |
| `RECOVERY_NOT_AVAILABLE` | Current-round recovery stream is unavailable. | Keep game state, show recovery warning, allow retry. |
| `RECOVERY_HISTORY_TRUNCATED` | Stream bound was reached before the earliest retained event. | Apply retained events only if explicitly supported; mark restore incomplete. |
| `INVALID_RECOVERY_REQUEST` | Missing round, invalid type, or invalid payload shape. | Do not retry unchanged request. |
| `WRONG_ROUND` | Request or drawing event does not match authoritative current round. | Ignore/reject; do not persist. |
| `NOT_DRAWER` | Sender is not current drawer. | Ignore/reject; do not persist. |
| `RESUME_RETRYABLE` | Temporary infrastructure/service failure. | Retry with bounded backoff. |

## 7. Ordering and recovery/live race

### 7.1 Ordering authority

- Redis Stream ID is the global order for recovery entries across strokes and control events.
- Binary `seqStart` remains per-stroke sequence and is used for stroke-local validation/diagnostics only.
- Client timestamps are not ordering fields.
- Gateway acceptance order is: authorize against current state, assign/receive the stream ID, record the accepted recovery event, then publish/fan out the live event.
- A stale round is rejected before it reaches Redis recovery history or live broadcast.

### 7.2 Chosen race strategy: server-side serialization

The existing binary protocol has no global event sequence. Adding one to every binary frame would change the established layout, so Phase 2 uses server-side serialization:

1. On `GET_CANVAS_STATE`, Gateway validates the session and marks that session `RECOVERING`.
2. Gateway reads a consistent current-round stream range and records its last stream ID.
3. Accepted drawing events continue to be recorded and sent live to other room members. Events for the recovering session are held in that session’s ordered outbound recovery buffer.
4. Gateway enqueues exactly one `SYNC_CANVAS_STATE` response for the recovering session.
5. Only after that response is queued does Gateway transition the session to `ACTIVE` and drain the buffered live frames in acceptance order.
6. The client clears its local canvas, applies recovery events, and then renders the drained live frames. `lastStreamId` is a diagnostic/deduplication cursor; the client does not need to infer ordering from timestamps.

TV3 must implement the recovery gate in the broadcaster/connection path. A frontend-only buffer is not sufficient unless every live drawing event also receives a global sequence, which would require a separate protocol change.

## 8. Lifecycle and stale-event rules

| Lifecycle event | Recovery state rule |
| --- | --- |
| `ROUND_STARTED` / first authoritative `PLAYING` state | Delete any old stream for the room, create the current-round stream/index entry, and reset the recovery generation. |
| Accepted drawing event | Require bound session, room membership, active game, current drawer, and exact current round. Record only after all checks pass. |
| `CLEAR_CANVAS` | Apply the atomic clear compaction rule in §5.4. |
| `ROUND_ENDED` | Stop accepting the old round immediately. Do not serve it as active canvas. Delete its stream during cleanup; TTL is a fallback. |
| Next round | Use a fresh round-keyed stream. Never read the prior round key for the new round. |
| `GAME_FINISHED` | Stop drawing, delete all indexed round streams and the index, invalidate active recovery requests, and never restore a finished round as active. |
| Room deleted | Delete indexed recovery keys when the deletion is observed; TTL remains a safety net because the current Room Service has no deletion event. |
| Old session after resume | Reject drawing/recovery if its Redis session binding no longer names that session. |

The Gateway’s local `DrawingRoomStateCache` may accelerate these decisions, but Game Service remains authoritative. If the cache is missing or ambiguous, TV3 must refresh with `GET_GAME_STATE` rather than accepting drawing or trusting a client round.

## 9. TV2 handoff

TV2 owns:

- reconnect loading/error state and bounded backoff UX;
- sending `RESUME_SESSION` after a new socket opens;
- handling the exact resume error table;
- issuing `GET_ROOM`, then `GET_GAME_STATE`, then `GET_CANVAS_STATE` in that order;
- clearing local canvas before applying `SYNC_CANVAS_STATE.payload.events`;
- applying `DRAW_START`, `DRAW_BATCH`, `DRAW_END`, and `CLEAR_CANVAS` recovery events;
- preserving normalized coordinates and supporting different canvas sizes;
- rendering the buffered/live frames after recovery and returning to normal drawing;
- ensuring the drawer does not draw while its session is not active/recovered;
- never trusting a client-supplied room ID or treating username as identity;
- keeping secret-word handling restricted to the existing viewer-specific game-state response.

TV2 does **not** design Redis keys, persist recovery history, decide authoritative player identity, or invent a second restore API.

## 10. TV3 handoff

TV3 owns:

- `RESUME_SESSION` membership verification and connection-context binding;
- the optional cross-Gateway session lease/binding and compare-and-delete cleanup;
- routing JSON and binary drawing through one authorization/normalization boundary;
- rejecting stale sessions, stale rounds, non-drawers, malformed recovery requests, and non-members;
- Redis Stream recording with the exact current-round key, bound, TTL, and index rules;
- assigning stream ordering and building the JSON `SYNC_CANVAS_STATE` response;
- atomic `CLEAR_CANVAS` compaction;
- the server-side recovery gate that serializes recovery before live frames for the recovering session;
- recovery access through shared Redis so Gateway 2 can serve a client previously connected to Gateway 1;
- round/game/room cleanup and recovery-state metrics.

TV3 does **not** implement React Canvas, localStorage behavior, reconnect toasts, or frontend loading UI.

## 11. TV1 ownership and open issues

TV1 remains responsible for integration, protocol review, security evolution, cross-service lifecycle events, and resolving conflicts between TV2 and TV3.

Open issues observed in the current code:

1. The JSON drawing handlers bypass the binary authorization/router path. Canvas recovery cannot be considered correct for JSON mode until this is unified.
2. Gateway currently accepts client-supplied `playerId` for `GET_GAME_STATE`, so membership/authentication is not yet authoritative for every command. `RESUME_SESSION` establishes the bound identity, but later command hardening is still required.
3. `DrawingRoomStateCache` is local and only partially lifecycle-synchronized. Game Service push events or a reliable refresh path are needed for prompt round transitions.
4. Room Service has no disconnect grace period and no room-deletion event. Membership survives socket loss; cleanup must be explicit or TTL-backed.
5. Room status strings differ between backend (`WAITING`/`PLAYING`/`FINISHED`) and frontend (`LOBBY`/`IN_GAME`/`FINISHED`). TV1/TV2 must normalize this during integration.
6. Game Service currently hard-codes round duration and derives `totalRounds` from `maxPlayers` in `startGame`; these defects affect lifecycle timing and the recovery bound.
7. A full page refresh loses the in-memory room ID even though `playerId` survives. Persisting or rehydrating the last room is a separate UX decision; this contract covers socket reconnect with a known room ID.
8. MVP `localStorage` identity is spoofable. JWT subject/authenticated principal must replace it before treating identity as secure.

## 12. Acceptance tests

1. A player in a `PLAYING` room loses the socket and reconnects. The new session resumes the same `playerId`; no duplicate Room Service membership is created.
2. After resume, `GET_ROOM` returns the existing membership and host, including after reconnecting to another Gateway.
3. After resume during a round, `GET_GAME_STATE` returns the authoritative current round, drawer, timer, and viewer-appropriate scores/secret-word visibility.
4. A reconnecting player requests `GET_CANVAS_STATE` for the active round and receives `SYNC_CANVAS_STATE` with normalized current-round drawing events.
5. A drawer continues drawing during another player’s recovery. The recovering session receives recovery first and then live events without a missing/duplicate ordering defect.
6. `CLEAR_CANVAS` before reconnect compacts history so the recovered state contains only the post-clear drawing.
7. Round 2 ends and round 3 starts. A round-2 recovery request is rejected and round-2 drawing is never served as round 3.
8. A player reconnects to Gateway 2 after drawing through Gateway 1. Gateway 2 reads the shared Redis Stream and recovers the current round.
9. A deleted/missing room returns `ROOM_NOT_FOUND` or `ROOM_ALREADY_CLOSED` as applicable and the client exits the room cleanly.
10. A finished game does not restore stale active-round canvas data.
11. Malformed, unauthorized, cross-room, stale-round, and non-drawer recovery/drawing requests are rejected without Redis history writes or broadcasts.
12. The stream reaches its configured bound/TTL. Recovery remains bounded, reports incomplete history when applicable, and Redis keys do not grow without limit.

## 13. Definition of done for this preparation task

This preparation is complete when TV2 and TV3 can implement the above flow independently without guessing event names, payload fields, identity semantics, Redis key design, ordering, or cleanup behavior. The minimal reconnect session bind is implemented; this is **not** a claim that canvas recovery is already implemented.
