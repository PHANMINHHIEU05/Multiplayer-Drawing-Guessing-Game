# 📋 Tiến Độ Phát Triển & Phân Công Nhóm
> **Dự án:** Multiplayer Drawing & Guessing Game  
> **Đề tài:** Game vẽ và đoán từ nhiều người chơi trực tuyến  
> **Kiến trúc:** Realtime Gateway + Room/Game/Chat Microservices  
> **Quy mô nhóm:** 4 thành viên  
> **Ngày cập nhật:** 22/08/2026  

---

## 📌 1. Trạng Thái Hiện Tại

Tài liệu này tổng hợp tiến độ tại thời điểm **22/08/2026**. Core backend và frontend gameplay MVP đã được xây dựng theo các milestone đã chốt. Các phần liên quan realtime drawing, tối ưu mạng, multi-Gateway và benchmark là công việc tiếp theo. Những mục deployment dưới đây được đánh dấu theo mức 'cần xác nhận' nếu chưa có bằng chứng chạy full-stack/staging.

| Hạng mục | Trạng thái | Ghi chú |
| :--- | :---: | :--- |
| **Redis + PostgreSQL local** | `HOÀN THÀNH` | Đã dùng cho Room/Game/Chat state và persistence. |
| **shared/protocol** | `HOÀN THÀNH` | room.proto, game.proto, chat.proto; gRPC/Protobuf. |
| **Realtime Gateway core** | `HOÀN THÀNH` | WebSocket nền, routing, connection management, gRPC clients. |
| **Room Service** | `HOÀN THÀNH` | Create/Join/Leave/Get/BeginGame/FinishGame + Redis. |
| **Game Service core** | `HOÀN THÀNH` | Word loading, round loop, hint generator, scoring, guess eval. |
| **Chat Service** | `HOÀN THÀNH` | Message routing, rate limit, membership check, Redis history. |
| **Frontend Gameplay MVP** | `HOÀN THÀNH` | Landing, Room Lobby, Game View, Chat, Scoreboard. |
| **Realtime Drawing JSON MVP** | `ĐANG LÀM` | Fast-path routing, canvas local, remote render. |
| **Docker Compose Local Full-Stack** | `CẦN XÁC NHẬN` | Cần verify lại 1 command `docker compose up --build` từ sạch. |
| **Railway / Staging Deploy** | `CẦN XÁC NHẬN` | Cần verify public WSS và env vars trên cloud. |

---

## 🎯 2. Milestone Tiếp Theo

Thứ tự phát triển được chốt như sau:

| Mốc | Công việc | Ưu tiên | Definition of Done |
| :---: | :--- | :---: | :--- |
| **M1** | Realtime Drawing JSON MVP | `P0 - NEXT` | Hai browser cùng room: drawer vẽ, guesser thấy nét gần realtime. |
| **M2** | DRAW_BATCH ~16 ms | `P0` | Giảm số WebSocket message bằng cách gom point. |
| **M3** | Binary WebSocket | `P0` | Giảm kích thước payload và overhead JSON. |
| **M4** | Redis Pub/Sub + 2 Gateway | `P0` | Drawing hoạt động khi hai user ở hai Gateway khác nhau. |
| **M5** | Backpressure | `P1` | Bounded queue, drop/coalesce stale draw, bảo vệ control messages. |
| **M6** | Reconnect + Canvas Recovery | `P1` | Heartbeat, reconnect, snapshot + delta. |
| **M7** | Network Inspector | `P1` | RTT, p95, jitter, bandwidth, batch size, queue, reconnect. |
| **M8** | Benchmark | `P0 (Báo cáo)` | JSON vs batch vs binary; 1 Gateway vs 2 Gateway. |
| **M9** | Semantic AI | `P2 (Optional)`| Fallback semantic matching tiếng Việt sau core network. |
| **M10**| Final integration & demo | `P0` | Clean build, full E2E test, deploy, tag v1.0-demo. |

---

## 👥 3. Phân Công Nhóm 4 Người

| Người | Ownership | Task | Kết quả bắt buộc |
| :--- | :--- | :--- | :--- |
| **TV1 - Lead / Integration + DevOps** | Architecture, protocol, integration, deployment, PR review | Chốt DRAW protocol; quản lý contract; tích hợp FE/Gateway/services; Docker Compose; Railway/Vercel staging; environment/config; merge và full-stack smoke test. | Protocol ổn định + full backend local/staging chạy được + các branch merge không phá core. |
| **TV2 - Frontend Realtime** | Canvas + Drawing Client | HTML Canvas; pointer events; normalized coordinate; gửi/nhận DRAW JSON; drawer-only UI; sau đó DRAW_BATCH và binary encode/decode phía client. | Browser A vẽ, Browser B thấy đúng nét; không dùng React state cho từng point. |
| **TV3 - Gateway Realtime** | Drawing fast-path + distributed fanout | DRAW_START/DRAW/DRAW_END/CLEAR; validate session/room/drawer; room broadcast; sau đó Redis Pub/Sub, multi-Gateway và backpressure. | Drawing chạy qua fast-path; sau phase scale, A ở Gateway1 và B ở Gateway2 vẫn thấy nét. |
| **TV4 - QA / Observability** | Network Inspector, test automation, reliability & benchmark | Làm task nhỏ tách biệt: test matrix 2 browser; bộ đếm messages/bytes; RTT/ping-pong nếu protocol hỗ trợ; Network Inspector UI; reconnect/failure test; benchmark scripts và tổng hợp số liệu. | Có checklist test tái lập, dashboard/metrics cơ bản và bảng benchmark phục vụ báo cáo; không phụ trách deployment. |

---

## 🧩 4. Task Nhỏ Có Thể Giao Ngay

> [!NOTE]
> Để tránh giao một mảng quá lớn cho mỗi người, các công việc được tách thành task nhỏ có dependency và Definition of Done rõ ràng. Có thể tạo issue/PR theo từng task dưới đây.

| Task | Người | Thời điểm | Nội dung | Done khi |
| :---: | :---: | :---: | :--- | :--- |
| **T1** | TV1 | Ngay | Chốt JSON drawing protocol v1 + error/event names. | FE và Gateway cùng dùng một contract; không còn invent tên message riêng. |
| **T2** | TV1 | Ngay | Hoàn thiện Docker Compose 6 thành phần + env baseline. | `docker compose up --build` dựng full backend local. |
| **T3** | TV2 | Ngay | Canvas local + pointer events + normalized x/y. | Drawer vẽ local mượt, resize không lệch. |
| **T4** | TV2 | Sau T1/T3 | Gửi/nhận DRAW JSON và render remote path. | Hai browser cùng room thấy cùng nét. |
| **T5** | TV3 | Ngay sau T1 | Gateway nhận DRAW và room broadcast fast-path. | DRAW không gọi Game Service; chỉ đúng room nhận. |
| **T6** | TV3 | Sau T5 | Validate drawer/session + CLEAR_CANVAS/DRAW_END. | Guesser gửi DRAW bị reject; clear/end đồng bộ. |
| **T7** | TV4 | Song song | Test matrix cho drawing: đúng room, sai room, non-drawer, reconnect, rapid input. | Có checklist PASS/FAIL tái lập cho mỗi case. |
| **T8** | TV4 | Song song | Network Inspector skeleton: TX/RX message count, bytes, timestamps/RTT. | UI/console metrics cập nhật khi chơi 2 browser. |
| **T9** | TV1 | Sau T4-T6 | Merge + integration test + deploy staging checkpoint. | Local E2E pass và staging không lỗi env/network. |
| **T10**| TV2 | Sprint sau | Point buffer ~16ms + DRAW_BATCH client. | Số message giảm rõ rệt mà nét vẫn mượt. |
| **T11**| TV3 | Sprint sau | Redis Pub/Sub + 2 Gateway; sau đó binary/backpressure. | A ở GW1, B ở GW2 vẫn thấy nét. |
| **T12**| TV4 | Sprint sau | Benchmark JSON point vs JSON batch vs binary; tổng hợp latency/p95/bandwidth. | Có script/kịch bản + bảng số liệu đưa được vào báo cáo. |

---

## 🚀 5. Sprint Hiện Tại: Realtime Drawing MVP

Sprint đầu tiên sau core chỉ tập trung làm cho đường truyền nét vẽ hoạt động bằng JSON WebSocket. Chưa làm binary, Pub/Sub hoặc AI trong cùng sprint để giảm rủi ro tích hợp.

| Vai trò | Công việc sprint | Output |
| :--- | :--- | :--- |
| **TV1 - Lead + DevOps** | Chốt DRAW_START/DRAW/DRAW_END/CLEAR_CANVAS; payload normalized x/y; drawer-only rule. Đồng thời hoàn thiện Docker Compose full stack và env local để các thành viên test cùng một baseline. | Protocol v1 + môi trường local full-stack ổn định. |
| **TV2 - Frontend** | Canvas local; pointerdown/move/up; gửi/nhận DRAW JSON; render remote line; resize không lệch. | A vẽ $\rightarrow$ B thấy. |
| **TV3 - Gateway** | Route drawing fast-path; validate room/session/drawer; broadcast chỉ trong đúng room; không gọi Game Service cho từng point. | Gateway DRAW MVP pass. |
| **TV4 - QA/Observability** | Viết test matrix Drawing MVP; tạo Network Inspector skeleton; đếm message TX/RX, bytes và timestamp/RTT cơ bản nếu có ping/pong. | Checklist test + metrics panel baseline. |

---

## ⚡ 6. Sprint Kế Tiếp: Batch + Binary + Scale

| Vai trò | Công việc |
| :--- | :--- |
| **TV1 - Lead + DevOps** | Định nghĩa DRAW_BATCH + seq + binary frame specification; tích hợp branch; deploy staging Railway/Vercel sau mỗi mốc ổn định. |
| **TV2 - Frontend** | Buffer pointer khoảng 16ms; DRAW_BATCH; encode/decode ArrayBuffer; đo batch size/message rate phía client. |
| **TV3 - Gateway** | Binary frame handling; Redis Pub/Sub; chạy 2 Gateway; bounded outbound queue/backpressure. |
| **TV4 - QA/Observability** | Mở rộng Network Inspector; test reconnect/failure scenarios; viết benchmark JSON single-point vs JSON batch vs binary và tổng hợp p95/bandwidth. |

---

## 🌿 7. Git Workflow

```text
main
 ├── feature/integration-devops
 ├── feature/drawing-frontend
 ├── feature/drawing-gateway
 └── feature/observability-qa
```

- **Quy tắc:**
  - Không commit trực tiếp vào `main` trong thời gian phát triển tính năng.
  - Mỗi branch có acceptance criteria rõ ràng và pull request riêng.
  - Thành viên 1 review contract/impact, kiểm tra môi trường integration và merge vào `main`.
  - Sau merge, Thành viên 1 và Thành viên 4 chạy smoke test/integration checklist; không chỉ dựa vào unit test.
  - Generated protobuf source và `target/` không commit nếu project không yêu cầu.

---

## ✅ 8. Definition of Done Cho Từng Phase

| Phase | Done khi |
| :--- | :--- |
| **Drawing JSON** | Drawer A vẽ $\rightarrow$ B thấy; B không vẽ được; coordinate đúng; không qua Game Service. |
| **Batch** | Batch ~16ms; số message giảm đáng kể; nét vẫn mượt. |
| **Binary** | FE/Gateway encode-decode pass; payload nhỏ hơn JSON theo số đo. |
| **Pub/Sub** | A ở Gateway1, B ở Gateway2 vẫn thấy drawing. |
| **Backpressure** | Client chậm không làm Gateway tăng queue vô hạn; control event không bị drop. |
| **Recovery** | Reconnect lấy lại state/canvas theo snapshot + recent deltas hoặc policy đã chọn. |
| **Benchmark** | Có kịch bản, cấu hình, số liệu latency/p95/bandwidth/CPU/memory, kết luận dựa trên đo. |
| **Final** | `mvn clean verify` + `frontend build` + full E2E + staging demo pass; tag commit demo. |

---

## ⚠️ 9. Các Rủi Ro Cần Theo Dõi

| Rủi ro | Tác động | Cách giảm |
| :--- | :--- | :--- |
| **Protocol thay đổi liên tục** | FE và Gateway conflict/bug | Lead khóa schema/version trước mỗi sprint. |
| **4 người cùng sửa Gateway** | Merge conflict cao | TV3 sở hữu fast-path Gateway; TV1 chỉ review/integration; TV4 làm tooling/metrics bên ngoài Gateway trừ khi có task được thống nhất. |
| **Drawing gửi từng point quá nhiều** | Bandwidth/message overhead | MVP trước, sau đó batching ~16ms và benchmark. |
| **Correct guess bị leak** | Lộ đáp án | Game Service quyết định; correct không đi Chat Service. |
| **Secret word trong log/state client** | Mất fairness | Viewer-specific GameState; không log secret. |
| **Redis/PubSub duplicate** | Nét vẽ lặp | Gateway instance id/sequence/dedupe strategy khi scale. |
| **Deploy khác local** | DNS/port/env lỗi | Docker full stack + Railway staging sớm. |

---

## 📝 10. Checklist Trước Khi Báo Cáo Cuối Kỳ

- [ ] Clean build toàn bộ backend từ root; test/verify PASS.
- [ ] Frontend lint/test/build PASS.
- [ ] Docker Compose dựng đủ backend + Redis/PostgreSQL từ đầu.
- [ ] Railway staging hoạt động; chỉ Gateway public.
- [ ] Vercel frontend kết nối WSS tới Gateway.
- [ ] Test ít nhất 2 browser end-to-end: create, join, start, draw, guess, chat, score, end.
- [ ] Test 2 Gateway + Redis Pub/Sub.
- [ ] Có benchmark JSON vs batching vs binary và 1 vs 2 Gateway.
- [ ] Ghi rõ trade-off, failure cases, fallback và giới hạn hiện tại.
- [ ] Tạo Git tag ổn định, ví dụ `v1.0-demo`.

---

## 📊 11. Tóm Tắt Tiến Độ

Core gameplay đã hoàn thành ở mức kiến trúc: **Gateway, Room Service, Game Service, Chat Service, shared gRPC protocol** và **frontend gameplay MVP**. Giai đoạn tiếp theo tập trung vào **realtime drawing** và **tối ưu mạng**. 



*Cách chia này giảm overlap code, tạo đầu việc rõ ràng cho 4 người và giúp mỗi thành viên đều có phần kỹ thuật chuyên sâu để trình bày khi bảo vệ dự án.*
