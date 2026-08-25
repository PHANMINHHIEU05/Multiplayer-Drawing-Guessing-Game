# 🌐 Member 03 Documentation: Realtime Gateway, Room Broadcast & Redis Pub/Sub

<div align="center">

![Role](https://img.shields.io/badge/Role-TV3_Distributed_Realtime-0ea5e9?style=for-the-badge&logo=redis)
![Status](https://img.shields.io/badge/Status-Ready_to_Start-22c55e?style=for-the-badge&logo=statuspage)
![Protocol](https://img.shields.io/badge/Drawing_Protocol-v1-8b5cf6?style=for-the-badge&logo=websocket)
![Stack](https://img.shields.io/badge/Tech-Spring_WebFlux_%7C_Reactor_%7C_Redis-red?style=for-the-badge&logo=spring)

</div>

---

## 📌 Executive Summary

Tài liệu này mô tả chi tiết phạm vi công việc của **Thành viên 03 (TV3 - Distributed Realtime Gateway)** trong dự án *Multiplayer Drawing & Guessing Game*.

Nhiệm vụ trọng tâm của TV3 là xây dựng **Drawing Fast-path tại Realtime Gateway**, đảm bảo dữ liệu vẽ sau khi được TV1 giải mã thành `DrawingMessage` có thể được:

1. Xác định đúng session / player / room.
2. Kiểm tra quyền vẽ và round hiện tại.
3. Broadcast đúng cho các client cùng phòng.
4. Đồng bộ giữa nhiều Gateway thông qua **Redis Pub/Sub**.
5. Tránh duplicate/self-echo khi nhiều Gateway cùng subscribe.
6. Giữ đường đi của drawing nhẹ, nhanh và không gọi Game Service cho từng batch.

> [!IMPORTANT]
> TV3 **không chịu trách nhiệm parse binary frame**. Binary WebSocket transport và `BinaryDrawingDecoder` thuộc TV1. TV3 bắt đầu công việc từ boundary `DrawingMessage`.

---

# 1. Mục tiêu kỹ thuật

Sau khi TV3 hoàn thành, hệ thống phải hỗ trợ được hai luồng:

### Single Gateway

```text
Player A (Drawer)
        ↓
WebSocket
        ↓
Realtime Gateway
        ↓
DrawingMessage
        ↓
Authorization / Round Validation
        ↓
Room Broadcast
        ↓
Player B, C cùng room
```

### Multi-Gateway

```text
Player A
   ↓
Gateway 1
   ├──────────────→ Local clients của Gateway 1
   │
   └── Redis Pub/Sub
            ↓
         Gateway 2
            ↓
     Local clients của Gateway 2
            ↓
         Player B
```

Mục tiêu cuối cùng:

> **Player A có thể vẽ trên Gateway 1 và Player B đang kết nối Gateway 2 vẫn nhìn thấy nét vẽ gần realtime, đúng room, đúng round, không duplicate và không cần đưa từng drawing batch qua Game Service.**

---

# 2. Boundary với TV1 và TV2

## TV1 cung cấp

TV1 chịu trách nhiệm:

```text
WebSocket BINARY
      ↓
BinaryDrawingDecoder
      ↓
DrawingMessage
```

TV3 nhận từ đây:

```text
DrawingMessage
      ↓
Routing / Authorization / Broadcast
```

TV3 **không cần biết**:

```text
uint16 coordinate encoding
UUID binary serialization
ByteBuffer layout
opcode bytes
BIG_ENDIAN parsing
```

## TV2 cung cấp

TV2 chịu trách nhiệm:

```text
Canvas
→ DRAW_START
→ DRAW_BATCH
→ DRAW_END
→ CLEAR_CANVAS
→ WebSocket
```

TV3 không xử lý Canvas hoặc Pointer Event.

---

# 3. Các loại DrawingMessage cần xử lý

TV3 phải xử lý đủ 4 semantic event:

```text
DRAW_START
DRAW_BATCH
DRAW_END
CLEAR_CANVAS
```

Mỗi event đều phải đi qua cùng pipeline:

```text
DrawingMessage
      ↓
Session Resolution
      ↓
Authorization
      ↓
Room / Round Validation
      ↓
Broadcast
```

Không viết 4 pipeline độc lập nếu không cần thiết.

---

# 4. Drawing Fast-path

Drawing là traffic có tần suất cao nên không đi qua business service cho mỗi frame.

## Không được làm

```text
Browser
→ Gateway
→ Game Service
→ Gateway
→ Browser
```

cho mỗi `DRAW_BATCH`.

Điều này sẽ tạo:

```text
nhiều gRPC call
nhiều serialization
nhiều context switch
latency cao hơn
load backend cao hơn
```

## Luồng đúng

```text
Browser
→ Gateway
→ validate bằng session + cached drawing state
→ broadcast
```

Game Service vẫn là nguồn authoritative cho game state, nhưng Gateway giữ **projection/cache nhẹ** phục vụ fast-path.

---

# 5. Session Resolution

Drawing message không chứa:

```text
playerId
roomId
```

TV3 phải lấy identity từ WebSocket session/connection metadata.

Thông tin tối thiểu cần resolve:

```text
sessionId
playerId
roomId
```

Nếu project đã có `ConnectionManager`, `ConnectionContext`, `ClientSession` hoặc abstraction tương đương thì phải reuse.

Không tạo duplicate source of truth.

Ví dụ conceptual:

```text
sessionId = ws-session-01
        ↓
ConnectionManager
        ↓
playerId = user-123
roomId   = ABC123
```

---

# 6. Authorization Rules

Trước khi broadcast drawing, Gateway phải kiểm tra:

```text
1. Session còn hợp lệ?
2. Player có room?
3. Room/game đang ở trạng thái cho phép drawing?
4. playerId == currentDrawerId?
5. message.round == currentRound?
```

Nếu một trong các điều kiện sai:

```text
REJECT / IGNORE DRAWING EVENT
```

Không broadcast.

Có thể log/metric:

```text
drawing.rejected.count
```

nhưng không spam log ở mức INFO cho từng frame.

---

# 7. Drawing State Cache

TV3 cần một cache nhẹ tại Gateway, ví dụ:

```text
roomId
  ↓
DrawingRoomState
  - currentRound
  - currentDrawerId
  - gameStatus
```

Ví dụ:

```text
roomId = ABC123
currentRound = 2
currentDrawerId = user-02
gameStatus = PLAYING
```

Mục đích:

```text
DRAW_BATCH
→ check cache O(1)
→ không gọi Game Service
```

> [!IMPORTANT]
> Cache này chỉ là fast-path projection, không thay thế Game Service authoritative state.

---

# 8. Cập nhật Drawing State Cache

TV3 phải inspect kiến trúc hiện tại để xác định Gateway nhận lifecycle state từ đâu.

Có thể update cache khi Gateway xử lý các event/response như:

```text
GAME_STARTED
ROUND_STARTED
ROUND_ENDED
GAME_FINISHED
```

hoặc response hiện có từ Game Service.

Không tự tạo một event bus giả nếu project hiện tại chưa có.

Nếu architecture hiện tại chưa expose đủ dữ liệu `drawerId/currentRound`, cần báo lại team và đề xuất integration point rõ ràng.

Không được fallback thành:

```text
mỗi DRAW_BATCH → gRPC GetGameState()
```

---

# 9. Local Room Broadcast

Sau khi drawing event hợp lệ, Gateway broadcast tới **các connection cùng room**.

Ví dụ:

```text
Room ABC123

A = drawer
B = guesser
C = guesser

Room XYZ999
D = player
```

A vẽ:

```text
A → Gateway
     ├── B ✅
     ├── C ✅
     └── D ❌
```

Không được broadcast global.

---

# 10. Sender Echo Policy

Drawer đã render local ngay trên Canvas.

Vì vậy ưu tiên policy:

```text
sender local-render
Gateway broadcast cho OTHER clients trong room
```

Tức là Gateway không cần echo nét về chính sender nếu frontend không cần ACK.

Phải thống nhất behavior này trong code để tránh drawer render double.

---

# 11. Drawing Router Architecture

Gợi ý structure:

```text
com.drawgame.realtime_gateway.drawing/

protocol/
  ... TV1

transport/
  ... TV1

routing/
  DrawingMessageRouter.java
  DrawingAuthorizationService.java
  DrawingRoomState.java
  DrawingRoomStateCache.java
  DrawingBroadcaster.java

redis/
  DrawingRedisPublisher.java
  DrawingRedisSubscriber.java
  RedisDrawingEnvelope.java
```

Tên class có thể thay đổi theo convention thật của project.

Không bắt buộc tạo tất cả class nếu abstraction hiện tại đã có tương đương.

---

# 12. DrawingMessageRouter

Router nên nhận boundary từ TV1:

```text
DrawingMessageHandler.handle(sessionContext, DrawingMessage)
```

Flow:

```text
handle(session, message)
      ↓
resolve connection metadata
      ↓
authorizationService.validate(...)
      ↓
localBroadcaster.broadcast(...)
      ↓
redisPublisher.publish(...)
```

Không parse raw WebSocket bytes trong router.

---

# 13. Redis Pub/Sub — Mục đích

Redis Pub/Sub được dùng để **fanout drawing event giữa các Gateway instance**.

Không dùng Redis Pub/Sub để:

```text
lưu lịch sử lâu dài
lưu từng point như database
thay PostgreSQL
```

Luồng:

```text
Gateway 1
    ↓ publish
Redis channel
    ↓ subscribe
Gateway 2
```

---

# 14. Redis Channel Strategy

Có hai hướng hợp lệ:

### Option A — Channel theo room

```text
drawing:ABC123
drawing:XYZ999
```

### Option B — Shared channel + roomId trong envelope

```text
drawing:events
```

payload:

```text
roomId
originGatewayId
drawing payload
```

TV3 phải inspect Redis infrastructure hiện tại và chọn cách phù hợp.

Không tạo hàng loạt subscribe/unsubscribe phức tạp nếu architecture hiện tại không cần.

---

# 15. Redis Drawing Envelope

Redis event phải chứa đủ metadata để Gateway khác route được event.

Ví dụ conceptual:

```text
originGatewayId
targetRoomId
payloadType
payload
```

`playerId` có thể không cần nếu event đã authorize tại source Gateway và receiver chỉ fanout.

Không được tin tưởng event Redis từ nguồn ngoài hệ thống.

Nếu Redis chỉ private/internal network thì vẫn nên validate basic shape.

---

# 16. Redis Payload Format

Ưu tiên không decode rồi encode lại drawing quá nhiều lần.

Nếu source đang nhận binary drawing, có thể cân nhắc publish:

```text
metadata envelope
+
raw drawing payload
```

hoặc representation domain phù hợp với implementation.

Mục tiêu:

```text
ít serialization overhead
```

nhưng vẫn phải giữ code dễ maintain.

Không premature optimize nếu làm architecture phức tạp quá mức.

---

# 17. Prevent Self-Echo / Duplicate

Khi Gateway 1:

```text
1. local broadcast
2. Redis publish
```

Gateway 1 có thể cũng nhận lại message từ Redis subscription.

Nếu không xử lý:

```text
B nhận local broadcast
+
B nhận Redis rebroadcast
= duplicate
```

Do đó cần có:

```text
originGatewayId
```

Ví dụ:

```text
originGatewayId = gateway-1
```

Khi Gateway 1 subscribe event:

```text
if event.originGatewayId == selfGatewayId
    ignore
```

Gateway 2:

```text
originGatewayId != selfGatewayId
→ broadcast local
```

---

# 18. Gateway Instance ID

Mỗi Gateway instance cần ID duy nhất trong runtime.

Có thể dùng:

```text
environment variable
container hostname
UUID generated at startup
```

Ví dụ:

```text
GATEWAY_INSTANCE_ID=gateway-1
```

Trong Docker Compose có thể set khác nhau cho hai instance khi test.

---

# 19. Multi-Gateway Flow Chi Tiết

```text
Player A
connected to Gateway 1

Player B
connected to Gateway 2

Both room ABC123

A draws
   ↓
Gateway 1 transport (TV1)
   ↓
DrawBatchMessage
   ↓
TV3 authorization
   ↓
valid drawer / valid round
   ↓
Gateway 1 local broadcast
   ↓
Redis publish
   room = ABC123
   origin = gateway-1
   ↓
Redis
   ↓
Gateway 1 subscriber
   → ignore self-origin

Gateway 2 subscriber
   → accept
   → lookup local connections(room ABC123)
   → broadcast binary drawing
   ↓
Player B
```

---

# 20. Không gọi gRPC trong Redis Subscriber

Gateway 2 đã nhận event drawing được source Gateway authorize.

Không cần:

```text
Redis event
→ Gateway 2
→ Game Service validation
→ broadcast
```

cho từng batch.

Receiver chủ yếu cần:

```text
room routing
basic event validation
local fanout
```

Nếu muốn defense-in-depth, chỉ dùng validation nhẹ từ cache, không tạo RPC per event.

---

# 21. Ordering

Drawing sử dụng:

```text
strokeId
seqStart
```

TV3 không cần xây reordering engine phức tạp trong phase này.

Nhưng phải:

```text
preserve order trong local processing tốt nhất có thể
không reorder batch một cách chủ động
không chạy parallel xử lý cùng stroke vô tội vạ
```

Sequence gap tracking thuộc TV4/observability phase sau.

---

# 22. Reactive / Non-blocking Rules

Realtime Gateway dùng Spring WebFlux / Reactor Netty.

TV3 không được dùng:

```text
block()
Thread.sleep()
Future.get()
blocking Redis client
blocking I/O trên event loop
```

Redis Pub/Sub nên sử dụng reactive/non-blocking integration phù hợp với dependencies hiện tại.

Không tạo Thread riêng cho mỗi room/client.

---

# 23. Concurrency Safety

Nhiều client và nhiều drawing event có thể đến đồng thời.

Nếu dùng map/cache:

```text
ConcurrentHashMap
```

hoặc abstraction thread-safe phù hợp.

Không dùng mutable `HashMap` shared giữa event-loop/thread mà không bảo vệ.

State update phải tránh race khi:

```text
round đổi
host/game state thay đổi
client disconnect
```

---

# 24. Failure Handling — Redis Down

Redis Pub/Sub có thể lỗi.

Single Gateway local clients vẫn nên hoạt động nếu có thể.

Ví dụ degradation:

```text
Redis unavailable
→ local broadcast vẫn chạy
→ cross-Gateway sync tạm lỗi
→ log/metric Redis publish failure
```

Không để Redis Pub/Sub lỗi làm kill WebSocket handler toàn bộ.

Fallback/retry nâng cao có thể để phase reliability sau.

---

# 25. Player Disconnect

Khi player disconnect:

```text
ConnectionManager
→ remove session
```

TV3 không giữ reference stale trong room broadcast list.

Nếu current drawer disconnect, game lifecycle xử lý drawer/round thuộc Game Service/flow hiện tại; TV3 chỉ cần đảm bảo stale session không tiếp tục gửi drawing.

---

# 26. Invalid Drawer Test

Case:

```text
A = drawer
B = guesser
```

B gửi `DRAW_BATCH`.

Expected:

```text
Gateway reject
no local broadcast
no Redis publish
```

Đây là test bắt buộc.

---

# 27. Invalid Round Test

Current round:

```text
round = 3
```

Client gửi:

```text
round = 2
```

Expected:

```text
reject
```

Không broadcast stale drawing của round cũ.

---

# 28. Cross-Room Isolation Test

```text
Room A:
player 1
player 2

Room B:
player 3
```

Player 1 vẽ.

Expected:

```text
player 2 nhận ✅
player 3 không nhận ❌
```

Test cả single Gateway và multi-Gateway nếu có thể.

---

# 29. Local Broadcast Test

Checkpoint đầu tiên phải chạy trước Redis:

```text
A + B cùng Gateway
A drawer
A gửi drawing
B nhận drawing
```

Không bắt đầu debug Redis trước khi local room broadcast PASS.

---

# 30. Redis Pub/Sub Test

Sau local broadcast PASS:

```text
Gateway 1
Gateway 2
Redis
```

Test:

```text
A → Gateway 1
B → Gateway 2
same room

A draws
→ B receives
```

---

# 31. Duplicate Test

Test một drawing batch có sequence cụ thể:

```text
strokeId = abc
seqStart = 100
```

Receiver chỉ nhận **một lần**.

Nếu nhận hai lần, self-echo prevention chưa đúng.

---

# 32. No Redis Persistence Test

Không tạo Redis data key kiểu:

```text
SET drawing:point:...
LPUSH drawing:points ...
```

cho từng point/batch trong phase này.

Redis Pub/Sub chỉ dùng cho fanout.

Canvas snapshot/recovery là phase sau.

---

# 33. Logging

Không log INFO từng `DRAW_BATCH`.

Có thể log:

```text
Gateway instance started
Redis subscription established
Redis publish failed
invalid drawer rejected
invalid round rejected
```

Drawing traffic success chỉ nên TRACE/DEBUG nếu cần.

---

# 34. Metrics Hook Chuẩn Bị Cho TV4

TV4 sẽ phụ trách observability sau.

TV3 nên chuẩn bị các hook/counter point rõ ràng, ví dụ:

```text
drawing.accepted
drawing.rejected
drawing.local.broadcast
drawing.redis.published
drawing.redis.received
drawing.redis.self_echo_ignored
```

Không cần xây dashboard trong task TV3.

Chỉ cần code dễ instrument về sau.

---

# 35. Không Làm Trong Task TV3

TV3 **không làm**:

```text
Binary frame parsing
Frontend Canvas
Pointer events
Binary ArrayBuffer client
Network Inspector
RTT dashboard
p95/jitter calculation
Reconnect hoàn chỉnh
Bounded queue/backpressure hoàn chỉnh
Load test chính thức
Benchmark JSON vs Binary
Canvas snapshot
Canvas recovery
Undo/Redo
Semantic AI
```

Không mở rộng scope sang TV4.

---

# 36. Implementation Roadmap

## Milestone 1 — Drawing Router

```text
DrawingMessageHandler
→ session resolution
→ room resolution
```

DoD:

```text
valid DrawingMessage vào router được
session/player/room resolve đúng
```

---

## Milestone 2 — Authorization

```text
room/player
→ drawing state cache
→ drawer + round validation
```

DoD:

```text
drawer hợp lệ → accept
non-drawer → reject
stale round → reject
```

---

## Milestone 3 — Local Room Broadcast

```text
accepted drawing
→ all other local connections in same room
```

DoD:

```text
A vẽ → B thấy
C room khác không thấy
```

---

## Milestone 4 — Redis Pub/Sub

```text
Gateway 1
→ Redis
→ Gateway 2
```

DoD:

```text
cross-Gateway drawing hoạt động
```

---

## Milestone 5 — Duplicate Prevention

```text
originGatewayId
→ ignore own event
```

DoD:

```text
mỗi receiver chỉ render mỗi batch một lần
```

---

# 37. Suggested Task Breakdown

| Task | Công việc | Mục tiêu |
|---|---|---|
| **T3.1** | Inspect Gateway/session architecture | Xác định boundary và metadata hiện có |
| **T3.2** | Implement `DrawingMessageRouter` | Nhận `DrawingMessage` từ TV1 |
| **T3.3** | Implement drawing authorization | Validate player/room/drawer/round |
| **T3.4** | Implement drawing state cache | O(1) fast-path validation |
| **T3.5** | Implement local room broadcast | A → Gateway → B cùng instance |
| **T3.6** | Implement Redis publisher | Publish drawing cross-instance |
| **T3.7** | Implement Redis subscriber | Nhận và local broadcast |
| **T3.8** | Implement self-echo prevention | Không duplicate event |
| **T3.9** | Multi-Gateway integration test | A Gateway1 → B Gateway2 |
| **T3.10** | Regression/build verification | Không phá Room/Game/Chat |

---

# 38. Test Matrix Bắt Buộc

| Case | Expected |
|---|---|
| Drawer gửi valid `DRAW_START` | Accept + broadcast |
| Drawer gửi valid `DRAW_BATCH` | Accept + broadcast |
| Drawer gửi `DRAW_END` | Accept + broadcast |
| Drawer gửi `CLEAR_CANVAS` | Accept + broadcast |
| Non-drawer gửi drawing | Reject |
| Sai round | Reject |
| Session chưa có room | Reject |
| Player room A, receiver room B | Không nhận |
| A/B cùng Gateway | B nhận |
| A Gateway1, B Gateway2 | B nhận qua Redis |
| Source Gateway nhận own Redis event | Ignore |
| Redis down | Local broadcast không crash |
| Client disconnect | Không giữ stale connection |

---

# 39. Definition of Done

TV3 được coi là hoàn thành khi:

```text
[ ] DrawingMessageHandler/Router nhận được DrawingMessage từ TV1.

[ ] Resolve sessionId/playerId/roomId đúng từ connection metadata.

[ ] Không lấy playerId/roomId từ drawing binary payload.

[ ] Có drawing state cache cho currentDrawer/currentRound.

[ ] Không gọi Game Service cho từng DRAW_BATCH.

[ ] Validate drawer đúng.

[ ] Validate round đúng.

[ ] Invalid drawing bị reject trước broadcast.

[ ] Local room broadcast hoạt động.

[ ] Room isolation hoạt động.

[ ] Sender echo policy rõ ràng, không double-render.

[ ] Redis Pub/Sub hoạt động.

[ ] Multi-Gateway drawing hoạt động.

[ ] Có originGatewayId hoặc giải pháp tương đương chống self-echo.

[ ] Không duplicate drawing qua Redis.

[ ] Redis Pub/Sub không persist từng drawing point.

[ ] Redis failure không crash toàn Gateway.

[ ] Không blocking Reactor event loop.

[ ] Tests quan trọng PASS.

[ ] Existing Gateway tests PASS.

[ ] Realtime Gateway build PASS.

[ ] Không sửa binary codec của TV1 nếu không có protocol change được thống nhất.
```

---

# 40. Acceptance Demo Cuối Task

Demo tối thiểu:

```text
Browser A
= drawer
= connected Gateway 1

Browser B
= guesser
= connected Gateway 2

Room = ABC123
Round = 2
Drawer = A

A pointerdown
    ↓
DRAW_START

A pointermove
    ↓
DRAW_BATCH

Gateway 1
    ↓
authorize
    ↓
Redis Pub/Sub
    ↓
Gateway 2
    ↓
B Canvas render

A pointerup
    ↓
DRAW_END
```

Phải chứng minh thêm:

```text
B thử vẽ
→ bị reject

Player C room khác
→ không nhận drawing

Gateway 1 nhận lại own Redis event
→ không broadcast duplicate
```

---

# 41. Output Bàn Giao

Khi hoàn thành, TV3 cần gửi Pull Request kèm report:

```text
1. Files created
2. Files modified
3. Drawing routing architecture
4. Session/room resolution strategy
5. Drawing state cache strategy
6. Drawer authorization strategy
7. Round validation strategy
8. Local broadcast strategy
9. Redis channel strategy
10. Redis payload/envelope strategy
11. originGatewayId/self-echo strategy
12. Multi-Gateway test setup
13. Failure handling
14. Tests added
15. Commands đã chạy
16. Test results
17. Build result
18. Known issues
19. TODO dành cho TV4
```

Không chỉ ghi:

> “Redis Pub/Sub đã xong”.

Phải nêu rõ đã test case nào và kết quả ra sao.

---

# 42. Handoff Cho TV4

Sau khi TV3 hoàn thành, TV4 sẽ tiếp tục trên luồng thật:

```text
Frontend
→ Gateway 1
→ Redis
→ Gateway 2
→ Frontend
```

TV4 phụ trách:

```text
Heartbeat / Reconnect
Bounded outbound queue
Backpressure
Drop / Coalesce stale DRAW_BATCH
Network Inspector
RTT / p95 / jitter
bytes/sec / messages/sec
sequence gaps
load test
benchmark
```

Do đó TV3 nên giữ code routing/broadcast có abstraction rõ để TV4 dễ instrument và thay outbound queue policy sau này.

---

# 43. Quy Tắc Cuối Cùng

Ưu tiên theo thứ tự:

```text
Correct room routing
→ Correct authorization
→ Local realtime correctness
→ Redis cross-Gateway correctness
→ Duplicate prevention
→ Reactive safety
→ Testability
→ Optimization
```

Không premature optimize trước khi local broadcast và multi-Gateway flow chạy đúng.

Không thay đổi wire protocol của TV1/TV2 nếu chưa thống nhất với nhóm.

---

<div align="center">

<i>Tài liệu Kỹ thuật Hợp phần Realtime Gateway & Distributed Drawing — Thành viên 03</i>

</div>
