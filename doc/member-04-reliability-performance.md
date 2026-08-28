# 🛡️ Member 04 Documentation: Reliability, Backpressure, Observability & Performance

<div align="center">

**Role:** TV4 — Reliability & Performance Engineering  
**Phase:** Sau khi Realtime Drawing Fast-path + Multi-Gateway đã hoạt động  
**Scope:** WebSocket Reliability • Backpressure • Network Inspector • Metrics • Load Test • Benchmark

</div>

---

## 1. Mục tiêu tổng quát

Thành viên 04 chịu trách nhiệm làm cho hệ thống realtime **ổn định hơn khi mạng không tốt, client chậm, mất kết nối hoặc tải tăng**, đồng thời xây dựng hệ thống **đo đạc và đánh giá hiệu năng thực tế**.

TV4 không xây lại Canvas, Binary Protocol hay Redis Pub/Sub. TV4 làm việc trên luồng realtime đã có sẵn để bổ sung các cơ chế reliability/performance.

Mục tiêu cuối cùng:

```text
Realtime Drawing đã chạy
        ↓
Heartbeat / Connection Health
        ↓
Reconnect / Backoff
        ↓
Bounded Queue / Backpressure
        ↓
Metrics Collection
        ↓
Network Inspector
        ↓
Load Test / Benchmark
        ↓
Báo cáo số liệu thực nghiệm
```

---

## 2. Boundary với các thành viên khác

### TV1 đã phụ trách

```text
Architecture
Binary Drawing Protocol
WebSocket Binary Transport
Docker / Integration / Deployment
```

TV4 **không sửa binary wire format** nếu không có thống nhất chung.

### TV2 đã phụ trách

```text
React Frontend
Canvas
Drawing Batching
JSON/Binary Drawing Client
```

TV4 chỉ bổ sung phần connection health, metrics và Network Inspector vào client hiện tại.

### TV3 đã phụ trách

```text
Drawing Fast-path
Drawer / Round Validation
Room Broadcast
Redis Pub/Sub
Multi-Gateway
```

TV4 không viết lại Redis fanout hoặc room routing. TV4 tập trung vào queue, backpressure, reliability và đo hiệu năng của luồng đó.

---

# PHẦN A — HEARTBEAT & CONNECTION HEALTH

## 3. Mục tiêu

Phát hiện sớm WebSocket mất kết nối hoặc connection không còn usable, đồng thời tạo nền tảng đo RTT.

Luồng đề xuất:

```text
Browser
   ↓ APP_PING
Gateway
   ↓ APP_PONG
Browser
   ↓
RTT = now - sentAt
```

> Lưu ý: Browser WebSocket API không chủ động gửi protocol-level Ping frame như server-side WebSocket API. Vì vậy nếu cần đo RTT từ browser, sử dụng **application-level PING/PONG message** hoặc tận dụng heartbeat đã có trong project.

### Yêu cầu

- Inspect heartbeat hiện tại trước khi tạo protocol mới.
- Nếu đã có heartbeat thì reuse.
- Không gửi heartbeat quá dày.
- Khoảng 1–5 giây/lần là đủ cho development/monitoring; giá trị phải cấu hình được.
- Không log INFO cho mỗi heartbeat.

### Metrics liên quan

```text
connection.state
heartbeat.sent
heartbeat.received
heartbeat.timeout
rtt.current_ms
rtt.avg_ms
```

---

# PHẦN B — WEBSOCKET RECONNECT

## 4. Reconnect Strategy

Khi connection bị đóng bất thường:

```text
CONNECTED
   ↓ disconnect
DISCONNECTED
   ↓
wait backoff
   ↓
CONNECTING
   ↓ success
CONNECTED
```

Không reconnect liên tục không delay.

Ưu tiên **exponential backoff có giới hạn**, ví dụ conceptual:

```text
1s → 2s → 4s → 8s → max 10s
```

Các giá trị thực tế phải để config, không hardcode rải rác.

Có thể thêm jitter nhỏ nếu cần để tránh nhiều client reconnect đồng thời.

## 5. Sau khi reconnect

Client phải khôi phục context cần thiết thay vì chỉ mở socket mới.

Flow:

```text
WebSocket reconnect success
        ↓
restore identity/session context
        ↓
GET_ROOM / rejoin flow nếu architecture yêu cầu
        ↓
GET_GAME_STATE
        ↓
restore lobby/game UI
```

Nếu project chưa có canvas snapshot/replay thì **không giả vờ canvas đã recover hoàn toàn**. Ghi rõ limitation.

Canvas recovery đầy đủ có thể là task nâng cao riêng nếu server chưa lưu snapshot/delta.

## 6. Reconnect Acceptance Cases

```text
CASE 1
Tắt mạng vài giây → bật lại
→ client tự reconnect.

CASE 2
Gateway restart
→ client reconnect sau khi Gateway lên lại.

CASE 3
Reconnect thành công
→ room/game state được refresh.

CASE 4
Reconnect thất bại nhiều lần
→ backoff tăng, không spam server.
```

---

# PHẦN C — BOUNDED QUEUE & BACKPRESSURE

## 7. Vấn đề cần giải quyết

Nếu một client nhận dữ liệu chậm:

```text
Gateway tạo event nhanh
        ↓
Outbound queue
        ↓
Client consume chậm
        ↓
Queue tăng liên tục
        ↓
RAM tăng
        ↓
Latency tăng
        ↓
Có nguy cơ ảnh hưởng toàn Gateway
```

Không để drawing traffic dùng queue unbounded mãi trong production path.

---

## 8. Bounded Outbound Queue

Mỗi connection nên có giới hạn outbound queue hoặc cơ chế tương đương phù hợp architecture Reactor hiện tại.

TV4 phải inspect `ConnectionManager`, Reactor `Sink`, outbound pipeline hiện tại trước khi sửa.

Không đoán API.

Mục tiêu:

```text
Connection A
→ bounded outbound capacity

Connection B
→ bounded outbound capacity
```

Client chậm không được làm queue tăng vô hạn.

---

## 9. Phân loại message theo độ quan trọng

Không được áp cùng một drop policy cho tất cả message.

### Control / Important Events

Ví dụ:

```text
ROUND_STARTED
ROUND_ENDED
GAME_FINISHED
PLAYER_GUESSED_CORRECTLY
CHAT_MESSAGE
ROOM_STATE_CHANGED
```

Các message này **không được drop tùy tiện**.

### High-frequency Drawing Events

```text
DRAW_BATCH
```

Drawing batch cũ có thể được ưu tiên thấp hơn khi client quá chậm.

Có thể nghiên cứu:

```text
Drop stale DRAW_BATCH
hoặc
Coalesce consecutive DRAW_BATCH
```

nhưng phải đảm bảo không làm hỏng stroke lifecycle một cách vô kiểm soát.

`DRAW_START`, `DRAW_END`, `CLEAR_CANVAS` cần được xem là quan trọng hơn `DRAW_BATCH` thông thường.

---

## 10. Backpressure Policy

TV4 cần thiết kế rõ policy, ví dụ:

```text
Queue còn chỗ
→ enqueue bình thường

Queue gần đầy
→ metric warning

Queue đầy
→ ưu tiên giữ control events
→ stale drawing batch có thể drop/coalesce
→ increment drop metric
```

Không silently drop mà không có metric.

### Metrics

```text
outbound.queue.size
outbound.queue.max
outbound.queue.overflow
outbound.draw.drop_count
outbound.draw.coalesce_count
```

---

# PHẦN D — SEQUENCE GAP TRACKING

## 11. Mục tiêu

Drawing protocol đã có:

```text
strokeId
seqStart
pointCount
```

TV4 dùng chúng để phát hiện application-level missing sequence.

Ví dụ:

```text
Batch A: seq 10..14
Batch B: seq 18..22

Expected next = 15
Received next = 18

sequence gap = 3
```

Tên metric:

```text
drawing.sequence_gap.count
```

**Không gọi đây là TCP packet loss**, vì WebSocket chạy trên TCP. Gap có thể do application drop/coalesce/reconnect hoặc logic khác.

---

# PHẦN E — NETWORK INSPECTOR

## 12. Mục tiêu

Xây một panel debug/development giúp quan sát realtime networking ngay trên Frontend.

Ví dụ:

```text
+---------------------------------------+
| NETWORK INSPECTOR                     |
+---------------------------------------+
| Connection       CONNECTED            |
| Draw Mode        BINARY_BATCH         |
| RTT              36 ms                |
| Avg RTT          40 ms                |
| p95 RTT          58 ms                |
| Jitter           7 ms                 |
| TX               52 msg/s             |
| RX               49 msg/s             |
| TX Bandwidth     8.4 KB/s             |
| RX Bandwidth     7.8 KB/s             |
| Draw Batches     48 /s                |
| Avg Points/Batch 4.2                  |
| Sequence Gaps    0                    |
| Reconnect Count  1                    |
+---------------------------------------+
```

Không cần UI đẹp như production. Ưu tiên đúng số liệu và dễ bật/tắt.

---

## 13. Metrics tối thiểu

### Connection

```text
connection state
reconnect count
heartbeat timeout count
```

### Traffic

```text
TX messages
RX messages
TX bytes
RX bytes
messages/sec
bytes/sec
```

### Drawing

```text
DRAW batches sent
DRAW batches received
points sent
points received
batches/sec
average points/batch
sequence gap count
```

### Latency

```text
current RTT
average RTT
p95 RTT
jitter
```

### Gateway queue nếu expose được

```text
outbound queue size
dropped draw batches
coalesced draw batches
```

---

## 14. Tính p95

Không cần hệ thống metrics phức tạp ngay từ đầu.

Có thể giữ một sliding window số mẫu RTT gần nhất, ví dụ 100–500 samples, sau đó tính percentile.

Không tính p95 từ 2–3 mẫu rồi coi là meaningful benchmark.

---

## 15. Jitter

Có thể định nghĩa đơn giản, rõ trong báo cáo, ví dụ trung bình độ chênh tuyệt đối giữa các RTT liên tiếp:

```text
jitter ≈ avg(|RTT[i] - RTT[i-1]|)
```

Nếu dùng định nghĩa khác phải ghi rõ.

Điều quan trọng là dùng **cùng một cách tính trong tất cả benchmark**.

---

# PHẦN F — LOAD TEST

## 16. Mục tiêu Load Test

Đo Gateway khi số client tăng.

Không chỉ test bằng tay 2 browser.

Cần có harness/script có khả năng tạo nhiều WebSocket clients.

Scenario gợi ý:

```text
10 clients
50 clients
100 clients
200 clients
...
```

Tăng dần theo khả năng máy test.

Không đặt mục tiêu số client quá cao nếu máy local không đủ tài nguyên.

---

## 17. Load Test Scenarios

### Scenario A — Idle Connections

```text
N clients connected
không drawing
chỉ heartbeat/control nhẹ
```

Đo baseline connection overhead.

### Scenario B — One Drawer, Many Viewers

```text
1 drawer
N-1 guessers
DRAW_BATCH đều đặn
```

Đây là scenario quan trọng nhất cho game.

### Scenario C — Multiple Rooms

```text
Room 1
Room 2
Room 3
...
```

Kiểm tra room isolation và tải fanout.

### Scenario D — Multi-Gateway

```text
Clients chia giữa Gateway 1 và Gateway 2
Redis Pub/Sub ở giữa
```

Đo overhead cross-instance fanout.

### Scenario E — Slow Client

Một số client cố tình consume chậm để kích hoạt backpressure.

Expected:

```text
Gateway không OOM
queue không tăng vô hạn
metrics phản ánh drop/coalesce
control flow vẫn hoạt động
```

---

# PHẦN G — BENCHMARK DRAWING PROTOCOL

## 18. Ba chế độ cần so sánh

Benchmark tối thiểu:

```text
JSON_POINT
vs
JSON_BATCH
vs
BINARY_BATCH
```

Nếu `JSON_POINT` không còn nằm trong production code, có thể tạo benchmark/debug mode riêng hoặc harness mô phỏng đúng semantic baseline.

Không làm hỏng production architecture chỉ để giữ baseline.

---

## 19. Chỉ số benchmark

Với cùng một drawing workload, đo:

```text
messages / second
bytes / second
average frame size
average points / batch
RTT / latency
p95 latency
jitter
Gateway CPU
Gateway memory
Redis traffic nếu đo được
```

Có thể thêm:

```text
outbound queue pressure
dropped/coalesced batch count
```

---

## 20. Quy tắc benchmark

Không so sánh các mode bằng workload khác nhau.

Ví dụ cùng một sequence drawing points phải được replay cho cả:

```text
JSON_POINT
JSON_BATCH
BINARY_BATCH
```

Giữ càng nhiều điều kiện giống nhau càng tốt:

```text
same machine
same number of clients
same drawing point rate
same duration
same room layout
same Gateway count
```

Mỗi scenario nên chạy nhiều lần thay vì chỉ 1 lần.

---

## 21. Không bịa kết quả

Tuyệt đối không ghi trước:

```text
"Binary giảm 92% bandwidth"
"Batch giảm 60% packet"
```

nếu chưa đo thực tế.

Các con số trong báo cáo cuối phải lấy từ benchmark thật.

Có thể đưa ra hypothesis trước benchmark, nhưng phải ghi rõ đó là **kỳ vọng**, không phải kết quả.

---

# PHẦN H — TEST MATRIX

## 22. Reliability Test Matrix

| Case | Tình huống                | Expected                                                       |
| ---- | ------------------------- | -------------------------------------------------------------- |
| R1   | Client mất mạng           | WebSocket detect disconnect                                    |
| R2   | Mạng trở lại              | Client reconnect bằng backoff                                  |
| R3   | Gateway restart           | Client reconnect khi server hoạt động lại                      |
| R4   | Reconnect nhiều lần       | Không spam reconnect liên tục                                  |
| R5   | Client chậm               | Queue không tăng vô hạn                                        |
| R6   | Queue đầy                 | Drawing policy được áp dụng và có metric                       |
| R7   | Control event khi tải cao | Không bị drop tùy tiện                                         |
| R8   | Sequence bị gap           | Inspector ghi nhận gap                                         |
| R9   | 2 Gateway                 | Metrics vẫn ghi nhận traffic đúng                              |
| R10  | Redis tạm mất             | Failure được log/quan sát rõ, Gateway không crash toàn process |

---

# PHẦN I — ROADMAP TV4

## 23. Milestone 1 — Instrumentation Foundation

```text
Connection metrics
TX/RX counters
byte counters
drawing counters
Network Inspector skeleton
```

### Done khi

- Inspector hiển thị được traffic thật.
- Không dùng fake metrics.

---

## 24. Milestone 2 — Heartbeat & Reconnect

```text
heartbeat
RTT
reconnect
backoff
reconnect count
```

### Done khi

- Restart Gateway và browser có thể reconnect.
- Network Inspector phản ánh trạng thái connection.

---

## 25. Milestone 3 — Backpressure

```text
bounded outbound queue
queue metrics
drawing overflow policy
drop/coalesce counters
```

### Done khi

- Slow client không làm queue tăng vô hạn.
- Có evidence bằng metric/test.

---

## 26. Milestone 4 — Load Test

```text
WebSocket load harness
idle test
1 drawer + N viewers
multiple rooms
multi-Gateway
slow client
```

### Done khi

- Có raw result có thể tái chạy.

---

## 27. Milestone 5 — Benchmark

```text
JSON_POINT
JSON_BATCH
BINARY_BATCH
```

### Done khi

- Có bảng số liệu thật.
- Có biểu đồ.
- Có nhận xét dựa trên số liệu.

---

# PHẦN J — DEFINITION OF DONE

## 28. TV4 hoàn thành khi

```text
[ ] Có cơ chế connection health/heartbeat phù hợp architecture hiện tại.

[ ] Có RTT measurement.

[ ] Có reconnect WebSocket tự động.

[ ] Reconnect sử dụng backoff, không reconnect loop vô hạn tốc độ cao.

[ ] Sau reconnect có refresh/restore room-game context ở mức architecture hiện hỗ trợ.

[ ] Outbound drawing queue không còn unbounded không kiểm soát.

[ ] Có backpressure policy rõ ràng.

[ ] Control events không bị áp cùng lossy policy với DRAW_BATCH.

[ ] Có metric cho queue overflow/drop/coalesce.

[ ] Có sequence-gap tracking.

[ ] Không gọi application-level sequence gap là TCP packet loss.

[ ] Có Network Inspector.

[ ] Inspector hiển thị connection state.

[ ] Inspector hiển thị drawing mode.

[ ] Có TX/RX messages.

[ ] Có TX/RX bytes.

[ ] Có messages/sec và bytes/sec.

[ ] Có batches/sec và average points/batch.

[ ] Có RTT / average RTT / p95 / jitter.

[ ] Có reconnect count.

[ ] Có load-test harness hoặc script có thể chạy lại.

[ ] Đã test nhiều client.

[ ] Đã test slow-client/backpressure case.

[ ] Đã benchmark JSON_POINT vs JSON_BATCH vs BINARY_BATCH hoặc ghi rõ baseline nào chưa thể triển khai và lý do.

[ ] Benchmark sử dụng workload tương đương.

[ ] Không bịa số liệu benchmark.

[ ] Có raw results / CSV / log hoặc output có thể kiểm chứng.

[ ] Có bảng/biểu đồ kết quả phục vụ báo cáo.

[ ] Existing Room/Game/Chat/Drawing flow vẫn PASS sau thay đổi.
```

---

# PHẦN K — NHỮNG GÌ TV4 KHÔNG LÀM

## 29. Out of Scope

TV4 không tự ý làm lại:

```text
❌ Binary Drawing Protocol
❌ Canvas drawing engine
❌ Redis Pub/Sub architecture
❌ Room routing
❌ Drawer authorization
❌ Game scoring
❌ Chat Service
❌ Word matching / AI semantic matching
```

Nếu cần thay đổi protocol hoặc routing để hỗ trợ reliability, phải trao đổi với owner tương ứng trước.

---

# PHẦN L — FINAL FLOW SAU KHI TV4 HOÀN THÀNH

## 30. Luồng hệ thống hoàn chỉnh

```text
Player A Canvas
      ↓
Drawing Batch / Binary
      ↓
WebSocket
      ↓
Gateway 1
      ↓
Bounded Queue / Metrics
      ↓
Redis Pub/Sub
      ↓
Gateway 2
      ↓
Bounded Queue / Metrics
      ↓
WebSocket
      ↓
Player B Canvas

Connection health:
Browser ⇄ Heartbeat ⇄ Gateway

Failure:
Disconnect
   ↓
Reconnect + Backoff
   ↓
Restore Room/Game State

Observability:
Realtime Traffic
   ↓
Metrics
   ↓
Network Inspector
   ↓
Load Test / Benchmark
```

---

# PHẦN M — KẾT QUẢ BÀN GIAO

## 31. Pull Request / Report cuối task

TV4 cần bàn giao report gồm:

```text
1. Files created
2. Files modified
3. Heartbeat strategy
4. Reconnect strategy
5. Backoff configuration
6. State restore behavior
7. ConnectionManager / outbound queue changes
8. Queue capacity strategy
9. Backpressure policy
10. Drop/coalesce policy
11. Metrics implemented
12. Network Inspector screenshots hoặc demo
13. Sequence-gap strategy
14. Load-test tool/harness
15. Test scenarios
16. Benchmark methodology
17. Raw benchmark results
18. Final benchmark tables
19. Charts
20. Commands used to run tests/load tests
21. Test/build results
22. Known limitations
23. TODO còn lại
```

Không chỉ báo:

> "Performance đã tối ưu."

Phải có **evidence bằng test và số liệu**.

---

# 32. Mục tiêu cuối cùng của TV4

TV4 phải giúp nhóm trả lời được bằng số liệu các câu hỏi:

```text
WebSocket mất kết nối thì hệ thống xử lý thế nào?

Client chậm có làm Gateway đầy RAM không?

Drawing queue đầy thì hệ thống ưu tiên/drop dữ liệu nào?

RTT và p95 latency thực tế là bao nhiêu?

JSON_POINT, JSON_BATCH và BINARY_BATCH khác nhau thế nào?

Một Gateway chịu được bao nhiêu client trong môi trường test?

Hai Gateway + Redis Pub/Sub làm latency/tài nguyên thay đổi ra sao?
```

Khi các câu hỏi trên có câu trả lời dựa trên **implementation + test + benchmark thực tế**, nhiệm vụ Thành viên 04 được coi là hoàn thành.

---

_Member 04 — Reliability, Backpressure, Observability & Performance Engineering_  
_Multiplayer Drawing & Guessing Game_
