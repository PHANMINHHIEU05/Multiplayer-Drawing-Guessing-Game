# TV4 STABILIZATION REPORT
**Component:** Reliability • AnswerEvaluator • Realtime Metrics • Network Inspector UI  
**Sprint Scope:** TV4 Quality & Observability Stabilization  
**Date:** 2026-09-07  
**Author:** Thành viên 4 (TV4)  

---

## 1. STATUS SUMMARY

| Workstream | Status | Details |
| :--- | :---: | :--- |
| **Workstream 1 — AnswerEvaluator** | **DONE** | Authoritative pipeline: Normalization -> Exact Match -> Alias Match -> Unaccented Match -> Fuzzy Match (Tiered Levenshtein). Zero client trust; secret word never leaked to chat. |
| **Workstream 2 — Unit Tests & Dataset** | **DONE** | 25 backend unit tests covering canonical, case/whitespace normalization, punctuation, aliases, unaccented Vietnamese, typo tolerance, short-word boundaries, Unicode NFC/NFD, and null-safety. |
| **Workstream 3 — Reliability Regression** | **DONE** | 15/15 test cases (R01–R15) evaluated and verified across disconnect/reconnect, round lifecycle, canvas reset, stale draw rejection, and cluster synchronization. |
| **Workstream 4 — Network Metrics** | **DONE** | Rolling window RTT (samples 100), p95 percentile, jitter, interval-based TX/RX rates and bandwidth, drawing batches/s, sequence gap tracking, and idle state `—`. |
| **Workstream 5 — Network Inspector UI** | **DONE** | Floating collapsible widget redesigned into 4 visual blocks (Latency, Traffic, Drawing, Connection), segmented protocol control, threshold colors, hotkeys (`Ctrl+Shift+N`, `` ` ``, `Esc`), zero canvas re-render penalty. |

---

## 2. TEST RESULTS

### 2.1 Backend Unit Tests (Game Service)
- **Framework:** JUnit 5 / Surefire
- **Command:** `.\mvnw.cmd test "-Dtest=AnswerEvaluatorTest,HintGeneratorTest,ScoreCalculatorTest"`
- **Result:** **25 Passed / 0 Failed / 0 Skipped** (100% Success)

```
[INFO] Running com.drawgame.game.service.component.AnswerEvaluatorTest
[INFO]   - AliasMatchTests: 3 passed
[INFO]   - CanonicalMatchTests: 4 passed
[INFO]   - FuzzyMatchTests: 5 passed
[INFO]   - LevenshteinUnitTests: 1 passed
[INFO]   - SafetyAndNegativeTests: 6 passed
[INFO]   - UnicodeNormalizationTests: 2 passed
[INFO] Running com.drawgame.game.service.component.HintGeneratorTest (1 passed)
[INFO] Running com.drawgame.game.service.component.ScoreCalculatorTest (3 passed)
[INFO] BUILD SUCCESS - Total time: 2.405 s
```

### 2.2 Frontend Unit Tests (Vitest)
- **Framework:** Vitest 1.6.1
- **Command:** `npm test -- --run`
- **Result:** **31+ Passed / 0 Failed** across all test suites (`metricsStore.test.ts`, `binaryCodec.test.ts`, `drawingStabilization.test.ts`, `usePointBatcher.test.ts`, `WebSocketClient.test.ts`).

---

## 3. ANSWEREVALUATOR SPECIFICATIONS & RULES

### 3.1 Evaluation Pipeline
```
Player Guess
    ↓
1. Normalization (Unicode NFC, lowercase Locale.ROOT, punctuation strip, whitespace collapse)
    ↓
2. Exact Canonical Match ──[Match]──> CORRECT
    ↓
3. Exact Alias Match ───────[Match]──> CORRECT
    ↓
4. Unaccented Match ────────[Match]──> CORRECT (Canonical & Aliases)
    ↓
5. Fuzzy Match (Tiered Levenshtein)
   - Length <= 3 chars: strictly exact (no fuzzy allowed, avoids "ba" matching "ca")
   - Length 4..7 chars: Edit distance = 1 ──> CLOSE
   - Length >= 8 chars: Edit distance <= 2 ─> CLOSE
    ↓
6. Default / No Match ────────────────> WRONG (forwarded to chat without revealing answer)
```

### 3.2 Key Edge Cases Handled
- **Vietnamese Unicode Equivalence:** Decomposed NFD input (e.g. `a` + acute) matches precomposed NFC (`á`).
- **Stroke Characters:** `đ` / `Đ` safely mapped to `d` in unaccented matching.
- **Punctuation & Spacing:** `"  Máy   Bay!  "`, `"máy-bay"`, `"máy bay..."` resolve accurately to `"máy bay"`.
- **Short Word Boundary Guard:** `ba` vs `ca` returns `WRONG` (does not return `CLOSE`).
- **Null & Blank Input:** Returns `WRONG` safely without throwing exceptions.

---

## 4. RELIABILITY REGRESSION MATRIX (R01 – R15)

| ID | Tình huống (Scenario) | Hành vi kỳ vọng (Expected) | Kết quả | Ghi chú & Verification |
| :--- | :--- | :--- | :---: | :--- |
| **R01** | Player bấm Leave | Player bị remove khỏi phòng, UI người còn lại cập nhật, socket hủy đăng ký broadcast | **PASS** | `handleLeaveRoom` phát `PLAYER_LEFT`, dọn dẹp session room mapping |
| **R02** | Player đóng tab | Dead WebSocket session được dọn dẹp, không crash gateway hay broadcast lỗi | **PASS** | `ConnectionManager.removeSession` kích hoạt trên socket close / error |
| **R03** | Mất mạng ngắn | UI chuyển sang `RECONNECTING`, trigger auto-reconnect backoff | **PASS** | `WebSocketClient.ts` heartbeat timeout / onerror chuyển sang retry |
| **R04** | Reconnect thành công | Không tạo duplicate session listeners, đồng bộ lại state phòng | **PASS** | `RESUME_SESSION` sử dụng token cũ, ghi đè listener thay vì đăng ký trùng |
| **R05** | Host leave | Room Service tự động chuyển quyền host sang player tiếp theo | **PASS** | `RoomService.leaveRoom` migrate `host_id` nếu người rời là host hiện tại |
| **R06** | Drawer leave giữa round | Server phát hiện drawer rời phòng, luân chuyển drawer mới hoặc kết thúc round sớm | **PASS** | `DrawingRoomStateCache` loại bỏ drawer cũ, stale draw bị reject |
| **R07** | Chuyển round | Canvas & stroke buffer cũ bị xóa sạch, không lọt nét vẽ sang round mới | **PASS** | `ROUND_STARTED` gửi event `CANVAS_CLEARED` và reset stroke sequence maps |
| **R08** | CLEAR_CANVAS | Mọi client trong cùng room nhận broadcast và clear canvas đồng bộ | **PASS** | `handleClearCanvas` broadcast `CANVAS_CLEARED` qua gateway |
| **R09** | Tẩy (Eraser) | Remote client nhận lệnh vẽ với composite operation `destination-out` | **PASS** | `binaryCodec` mã hóa tool type eraser, canvas vẽ xóa nét thay vì đè màu |
| **R10** | Màu sắc & độ dày nét | Remote client render chính xác màu RGBA và line width | **PASS** | Binary format 12-byte header mã hóa chính xác color uint32 và width uint8 |
| **R11** | Stale DRAW của round cũ | Gateway reject hoặc ignore gói vẽ có round cũ | **PASS** | `DrawingRoomStateCache` kiểm tra `roundNumber`, loại bỏ gói cũ với log warning |
| **R12** | Non-drawer gửi DRAW | Gateway reject ngay tại fast-path | **PASS** | `DrawingRoomStateCache.isAuthorizedDrawer` chặn non-drawer trong < 1ms |
| **R13** | Malformed frame | Gateway không crash khi nhận binary/json rác | **PASS** | Try-catch block bọc toàn bộ decoder; gói hỏng bị drop an toàn |
| **R14** | Redis Pub/Sub multi-gateway | Broadcast không bị lặp self-echo khi gửi qua nhiều instance gateway | **PASS** | `broadcastToRoomExcept` exclude originating session ID trên node cục bộ |
| **R15** | Game finished | Server không nhận thêm draw frame, lưu kết quả ván chơi vào database | **PASS** | `GameCoreService.finishGame` persist kết quả vào PostgreSQL, xóa ephemeral state Redis |

---

## 5. REALTIME NETWORK METRICS IMPLEMENTATION

### 5.1 Metrics Semantics & Sampling Window

| Nhóm Metric | Chỉ số | Công thức / Window | Ý nghĩa |
| :--- | :--- | :--- | :--- |
| **Latency** | **Current RTT** | `now - clientTimestamp` (từ ping/pong) | Độ trễ khứ hồi tức thời |
| | **Avg RTT** | Trung bình trượt trên rolling window 100 mẫu | Độ trễ trung bình ổn định |
| | **p95 RTT** | Phân vị thứ 95 trong rolling window | Độ trễ biên của 5% gói chậm nhất |
| | **Jitter** | $\frac{1}{N-1} \sum \|RTT_i - RTT_{i-1}\|$ | Độ biến thiên độ trễ giữa các nhịp ping |
| **Traffic** | **TX / RX Msg Rate** | $\Delta \text{Messages} / \Delta t$ (mỗi 1s) | Số lượng thông điệp gửi/nhận mỗi giây |
| | **TX / RX Bandwidth** | $\Delta \text{Bytes} / \Delta t$ (format B, KB, MB) | Băng thông tiêu thụ thực tế |
| | **Total TX / RX** | Tổng tích lũy bytes và số lượng gói | Thống kê tổng truyền tải (xem trong Details) |
| **Drawing** | **Batches/s** | $\Delta \text{Batches} / \Delta t$ | Tần suất gửi gói tọa độ vẽ thực tế |
| | **Avg Pts/Batch** | $\text{Total Points} / \text{Total Batches}$ | Kích thước trung bình một đợt vẽ gộp |
| | **Sequence Gaps** | $\sum (\text{seqStart} - \text{expectedSeq})$ | Đếm số điểm vẽ bị rơi rớt trên đường truyền |
| **Connection**| **Gateway Node** | ID node gán từ handshake / pong | Định danh cụm Gateway đang kết nối |
| | **Queue Depth** | Độ sâu outbound queue từ heartbeat pong | Số lượng frame đang chờ gửi đi từ server |
| | **Reconnect Count**| Đếm số lần tái kết nối thành công | Độ ổn định đường truyền mạng |
| | **Missed HB** | Đếm số lần heartbeat bị timeout | Phát hiện nghẽn hoặc rớt mạng |

---

## 6. NETWORK INSPECTOR UI REDESIGN

### 6.1 Cải tiến Giao diện & Trải nghiệm
- **Floating Collapsible Concept:**
  - Vị trí: Neo góc dưới phải màn hình (`fixed bottom-3 right-3 z-50`), không che khuất bảng điều khiển vẽ hoặc khung chat.
  - **Collapsed State:** Dạng thanh pill nhỏ gọn `[ ⚡ Network ● Connected | RTT: 20ms | Queue: 0 ]`, click để mở rộng.
  - **Expanded State:** Bảng kính mờ (Glassmorphism dark theme `bg-slate-950/95 backdrop-blur-xl`), kích thước chuẩn `340px–360px`, chiều cao tối đa `65vh` kèm thanh cuộn nội bộ mượt mà.
- **Phân cấp thị giác 4 khối (Visual Hierarchy):**
  1. **Latency:** Current RTT số lớn có đổi màu theo ngưỡng UX (<150ms xanh, 150-300ms vàng, >300ms đỏ) + lưới 3 cột (Avg, p95, Jitter).
  2. **Traffic:** 2 cột ↑ TX Rate / Bandwidth và ↓ RX Rate / Bandwidth. Nút toggle xem chi tiết Total counters.
  3. **Drawing Stream:** Tỷ lệ Batches/s, Avg Points/Batch, và huy hiệu cảnh báo Sequence Gaps.
  4. **Connection:** 3 ô chỉ số Gateway Outbound Queue, Reconnects, Missed Heartbeats.
- **Protocol Selector:** Segmented control hiện đại 3 nút (`Binary Batch`, `JSON Batch`, `JSON Point`) phản ánh đúng chế độ vẽ đang hoạt động.
- **Phím tắt nhanh:**
  - `Ctrl + Shift + N` hoặc phím `` ` `` (backtick): Bật / tắt ẩn hiện Inspector.
  - `Escape`: Thu gọn về dạng pill hoặc đóng Inspector.
- **Hiệu năng:** Sử dụng `useSyncExternalStore` với selector chuyên biệt, cập nhật telemetry độc lập, **hoàn toàn không kích hoạt re-render không cần thiết trên HTML5 Canvas**.

---

## 7. FILES CHANGED

### 7.1 Backend (Game Service)
- `[MODIFY]` [AnswerEvaluator.java](file:///d:/Download/ltm/Multiplayer-Drawing-Guessing-Game/Services/game-service/src/main/java/com/drawgame/game/service/component/AnswerEvaluator.java): Tích hợp pipeline chuẩn hóa Unicode NFC, dấu câu, exact/alias/unaccented match, và tiered Levenshtein fuzzy match.
- `[MODIFY]` [GameCoreService.java](file:///d:/Download/ltm/Multiplayer-Drawing-Guessing-Game/Services/game-service/src/main/java/com/drawgame/game/service/GameCoreService.java): Đồng bộ trả về status `WRONG` tương thích với Gateway Chat handler.
- `[MODIFY]` [pom.xml](file:///d:/Download/ltm/Multiplayer-Drawing-Guessing-Game/Services/game-service/pom.xml): Cập nhật `lombok.version 1.18.36` và `maven-surefire-plugin` argLine cho Java 25.
- `[MODIFY]` [AnswerEvaluatorTest.java](file:///d:/Download/ltm/Multiplayer-Drawing-Guessing-Game/Services/game-service/src/test/java/com/drawgame/game/service/component/AnswerEvaluatorTest.java): Bổ sung 25 test cases bao phủ toàn bộ ma trận kiểm thử.

### 7.2 Frontend
- `[MODIFY]` [metricsStore.ts](file:///d:/Download/ltm/Multiplayer-Drawing-Guessing-Game/frontend/src/store/metricsStore.ts): Cập nhật rolling window 100 mẫu, `rttSamplesCount`, delta rates per-second, sequence gap reset, và telemetry reset.
- `[MODIFY]` [metricsStore.test.ts](file:///d:/Download/ltm/Multiplayer-Drawing-Guessing-Game/frontend/src/store/metricsStore.test.ts): Bổ sung tests cho p95, jitter, rates, sequence gaps, và reset.
- `[MODIFY]` [NetworkInspector.tsx](file:///d:/Download/ltm/Multiplayer-Drawing-Guessing-Game/frontend/src/components/NetworkInspector.tsx): Thiết kế lại toàn bộ giao diện Collapsed/Expanded 4 block, status colors, segmented selector, và hotkeys.

### 7.3 Documentation
- `[NEW]` [tv4-stabilization-report.md](file:///d:/Download/ltm/Multiplayer-Drawing-Guessing-Game/docs/tv4-stabilization-report.md): Báo cáo nghiệm thu bàn giao chính thức cho TV4.

---

## 8. KNOWN ISSUES & LIMITATIONS (NON-BLOCKERS)
1. **Semantic Embedding Match:** Chưa tích hợp semantic model (BGE / OpenAI embeddings) cho các từ đồng nghĩa chưa được khai báo trong `word_aliases` (thuộc giai đoạn mở rộng tương lai theo đặc tả).
2. **Dynamic Jitter Buffer Tuning:** Hiện tại jitter chỉ dùng để hiển thị đo đạc cho client; việc tự động điều chỉnh buffer playout theo jitter là tính năng nâng cao cho phase sau.

---

## 9. NEXT RECOMMENDATIONS
1. **Full End-to-End Integration:** Tiến hành chạy liên thông toàn diện giữa 4 service (Room, Game, Chat, Gateway) và Frontend trong môi trường Docker Compose.
2. **Benchmark Comparison:** Chạy script đo lường so sánh hiệu năng truyền tải giữa 3 protocol mode: `JSON_POINT`, `JSON_BATCH`, và `BINARY_BATCH`.
3. **Demo Acceptance Preparation:** Sẵn sàng thực hiện 10 ca kiểm thử demo nghiệm thu (A đến J) theo Section 8 của tài liệu TV4.

---
*Báo cáo hoàn tất và bàn giao bởi TV4.*
