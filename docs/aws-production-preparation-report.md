# Báo cáo chuẩn bị production trên AWS EC2

Ngày kiểm tra: 2026-09-23. Phạm vi là chuẩn bị và kiểm thử cục bộ cấu hình cho một EC2 Ubuntu 24.04 amd64; không gọi AWS API, không kết nối hoặc deploy lên EC2.

## A. Hiện trạng trước khi sửa

- `docker-compose.yml` là cấu hình phát triển: PostgreSQL, Redis, frontend, hai Gateway (Gateway 2 qua profile `multi-gateway`) và HTTP/gRPC của Room/Game/Chat có host port mapping.
- Compose phát triển còn giá trị mặc định không an toàn cho mật khẩu và JWT để tiện chạy local. Chúng không được dùng trong override production.
- Frontend có endpoint pool dành cho phát triển trực tiếp tới `localhost:8080`/`localhost:8090`; frontend Nginx đã có SPA fallback.
- Redis đã dùng AOF và named volume; PostgreSQL đã có named volume. PostgreSQL, Redis có healthcheck; Java service có Actuator health endpoint nhưng không có Docker healthcheck riêng.
- Các Java service đã chờ PostgreSQL/Redis khỏe khi phụ thuộc vào chúng; một số `depends_on: service_started` chỉ đợi container bắt đầu, không đảm bảo ứng dụng gRPC đã sẵn sàng.
- Trước thay đổi chưa có Caddy, chính sách log rotation đồng nhất, giới hạn bộ nhớ JVM/container hoặc Compose override production.

## B. Kiến trúc production cuối

```text
                          Internet
                             |
                        TCP 80/443
                             v
                  Caddy (TLS, redirect, LB)
                    |                 |
                frontend       /ws, /ws/*
                Nginx SPA       /       \
                              Gateway 1  Gateway 2
                                  \       /
                           Redis Pub/Sub/Streams
                             |             |
                     Room / Game / Chat services
                             |             |
                         PostgreSQL
```

Toàn bộ stack chạy trên một EC2. Backend dùng Docker service DNS, không dùng IP container cố định. Hai Gateway chia sẻ Redis và JWT secret như kiến trúc hiện tại. Caddy cân bằng các kết nối WebSocket mới; không bật sticky session. Nếu Gateway đang giữ socket dừng, socket đó bị ngắt rồi frontend reconnect/resume qua Gateway còn sống.

Một EC2 không phải HA cấp hạ tầng: lỗi host hoặc instance sẽ làm toàn bộ dịch vụ ngừng. Hai Gateway chỉ tăng khả năng chịu lỗi ở tầng ứng dụng.

## C. Thiết kế Compose

`docker-compose.prod.yml` kế thừa Compose hiện tại, dùng profile `multi-gateway`, xóa host bindings nội bộ bằng `!reset []`, bỏ tên container cố định để Compose quản lý vòng đời, và thêm Caddy. Mạng `drawing-game-internal` là internal; Caddy nối thêm vào mạng edge để phục vụ Internet/ACME. Cấu hình HTTP thử IP nằm riêng trong `docker-compose.http-test.yml`, không thay cấu hình TLS cuối.

PostgreSQL và Redis giữ named volume, healthcheck và restart policy hiện có. Gateway, Room, Game, Chat giữ `unless-stopped`; container Caddy cũng dùng policy này. Các Java container được giới hạn heap theo cgroup (`InitialRAMPercentage=10`, `MaxRAMPercentage=50`) và có hard memory limit. Giới hạn tổng là 5.5 GiB trên máy 8 GiB, để lại khoảng 2.5 GiB cho hệ điều hành/cache; đây là điểm khởi đầu cần theo dõi bằng `docker stats`, không phải kết quả benchmark tải.

## D. Ma trận cổng

| Cổng | Công khai | Dịch vụ / ghi chú |
|---:|---|---|
| 22/tcp | Có, chỉ IP quản trị qua Security Group | SSH; không publish trong Compose |
| 80/tcp | Có | Caddy HTTP và chuyển hướng HTTPS; HTTP test tạm |
| 443/tcp | Có | Caddy HTTPS/WSS |
| 3000/tcp | Không | Frontend dev; bỏ host binding production |
| 8080/tcp | Không | Gateway 1, nội bộ |
| 8090/tcp | Không | Gateway 2 host mapping của dev; production không publish; container port là 8080 |
| 8081/tcp, 9091/tcp | Không | Room HTTP/Actuator, gRPC |
| 8082/tcp, 9092/tcp | Không | Game HTTP/Actuator, gRPC |
| 8083/tcp, 9093/tcp | Không | Chat HTTP/Actuator, gRPC |
| 5432/tcp | Không | PostgreSQL |
| 6379/tcp | Không | Redis |

Kết quả `docker compose config --format json` với production override: chỉ Caddy có `ports`, cụ thể `80:80` và `443:443`; tất cả service còn lại không có host port. Stack HTTP thử cục bộ chỉ bind loopback `127.0.0.1:18080`.

## E. Caddy

- `infra/caddy/Caddyfile` dùng `{$APP_DOMAIN}`, phục vụ frontend qua `frontend:80`, giữ SPA fallback của Nginx và proxy `/ws`, `/ws/*` tới `realtime-gateway:8080` cùng `realtime-gateway-2:8080`.
- `round_robin` phân phối kết nối mới; không sticky session. Caddy chủ động health-check `/actuator/health` mỗi 10 giây (timeout 2 giây, đánh dấu lỗi sau 2 lần) và có passive failure handling.
- Domain site block bật automatic HTTPS và HTTP→HTTPS redirect của Caddy. `caddy validate` xác nhận cấu hình hợp lệ và Caddy bật cơ chế redirect tự động.
- `caddy-data` và `caddy-config` là named volume để dữ liệu TLS/cấu hình tồn tại qua việc tạo lại container.
- Caddyfile production không hardcode domain. Chứng chỉ thật chưa thể được cấp/kiểm tra trong local test vì `game.example.com` chỉ là placeholder, không có DNS/EC2 trong task.

## F. Frontend WebSocket

Production build đặt `VITE_WS_URL`, `VITE_WS_URLS` rỗng và bật `VITE_WS_SAME_ORIGIN`. Khi đó frontend tự suy ra `ws://<host>/ws` trên HTTP và `wss://<host>/ws` trên HTTPS. Domain không bị bake vào bundle. Development vẫn giữ `VITE_WS_URLS` cho hai Gateway trực tiếp; mặc định dev cũ không đổi.

## G. Biến môi trường

Tên biến production được dùng: `APP_DOMAIN`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `GAME_SESSION_JWT_SECRET`, `GAME_SESSION_TOKEN_TTL_MINUTES`, `WS_ALLOWED_ORIGINS`. `APP_DOMAIN`, DB credential, JWT secret và allowlist là bắt buộc qua Compose interpolation; Redis/service DNS cố định theo topology. `.env.production.example` chỉ là mẫu placeholder an toàn, không chứa credential thật.

## H. Lưu trữ

- PostgreSQL: named volume `postgres-data`; kiểm tra row thử nghiệm vẫn còn sau `docker compose restart postgres`.
- Redis: named volume `redis-data` và AOF theo cấu hình hiện tại; không đổi semantics Pub/Sub/Streams.
- Caddy: named volume `caddy-data` và `caddy-config`.
- `docker compose down` không xóa các named volume; xác nhận thực tế sau khi dừng stack test. Không dùng `down -v` trừ khi chủ ý xóa dữ liệu.

## I. Bảo mật

- Production chỉ cho Caddy publish 80/443; SSH 22 thuộc Security Group. DB, Redis, HTTP/gRPC backend và Gateway không có host binding.
- `WS_ALLOWED_ORIGINS` phải khớp origin HTTPS của frontend; không dùng wildcard. Cả hai Gateway nhận cùng secret/allowlist từ env.
- `.gitignore` loại `.env.production` và `*.pem`; `.dockerignore` loại `.env*`, key và file build/test không cần thiết. `frontend/.dockerignore` cũng loại `.env*` khỏi build context riêng.
- Rà soát Git-tracked files không tìm thấy private key PEM, AWS access key dạng chuẩn hoặc production secret thật. Cấu hình Compose dev gốc vẫn có fallback yếu chỉ dành cho local; production override bắt buộc nhập giá trị từ env.
- `APP_DOMAIN=game.example.com` là placeholder tài liệu, không phải domain/IP production.

## J. Healthcheck và restart

- PostgreSQL: `pg_isready`; Redis: `redis-cli ping`.
- Room/Game/Chat/Gateway có Actuator `/actuator/health`; Caddy dùng endpoint đó để health-check hai Gateway. Frontend và Java app không được thêm Docker healthcheck giả.
- Compose dependencies giữ health condition cho PostgreSQL/Redis; các quan hệ `service_started` chỉ bảo đảm thứ tự khởi chạy container, không bảo đảm readiness gRPC.
- Mọi production container có `restart: unless-stopped`. Gateway 1 được stop/start trong smoke test; EC2 reboot thật chưa thực hiện.
- Mọi service dùng `json-file`, `max-size=10m`, `max-file=3`.

## K. Kiểm thử production-style cục bộ

Stack validation được chạy dưới project riêng `drawgame-prod-validation`, chỉ bind HTTP thử vào loopback, không đụng stack dev đang có. Để các Node WebSocket test không có header `Origin` chạy được, riêng hai Gateway của stack test được chạy với `WS_ALLOWED_ORIGINS` là whitespace (Spring xem là blank). Đây là ngoại lệ chỉ cho test local, không phải giá trị production. Allowlist strict riêng cũng đã được thử từ browser với origin khớp chính xác. Không thay `.env.production.example` bằng secret thật.

Các kết quả:

- Compose production và HTTP-test overlay parse thành công; Caddyfile production validate thành công.
- Production Docker images build và toàn bộ 9 service chạy; PostgreSQL/Redis healthy.
- Qua Caddy: trang `/` trả HTTP 200; route SPA giả lập `/game/room/example` cũng trả HTTP 200.
- 12/12 production proxy smoke checks đạt: Caddy chọn cả hai Gateway; tạo/join/ready/start; binary drawing cross-Gateway; Canvas recovery từ Redis stream; chat/guess; round kế; dừng Gateway 1, reconnect/resume qua Gateway 2, phục hồi game/canvas; hoàn tất ván và rematch.
- Multi-client QA: 30/30; manual gameplay/reconnect regression: 28/28 qua cùng Caddy URL.
- Frontend: `npm test` 41/41, lint đạt; Docker frontend build chạy `tsc`/Vite thành công.
- PostgreSQL probe được tạo, còn nguyên sau Compose restart. Chạy `docker compose down` không có `-v`; bốn named volume của project còn nguyên sau khi containers/network được gỡ. Stack dev trước đó vẫn đang chạy với port mapping ban đầu.
- Caddy local HTTP thử chứng minh HTTP/WS proxy; chứng chỉ/TLS công khai không thể kiểm thử khi chưa có domain/DNS trỏ về EC2.

Lệnh smoke test local (chỉ dùng project cô lập, không chạy trên production):

```bash
HTTP_TEST_ADDRESS=127.0.0.1:18080 WS_ALLOWED_ORIGINS=' ' \
docker compose --env-file .env.production.example \
  -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.http-test.yml \
  --profile multi-gateway --project-name drawgame-prod-validation \
  up -d --build

PROD_SMOKE_ALLOW_NO_ORIGIN=1 PROD_SMOKE_TEST_FAILOVER=1 \
PROD_SMOKE_COMPOSE_PROJECT=drawgame-prod-validation \
PROD_SMOKE_COMPOSE_ENV=.env.production.example \
GW_URL=ws://127.0.0.1:18080/ws \
node tools/e2e/production-proxy-smoke.mjs

docker compose --env-file .env.production.example \
  -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.http-test.yml \
  --profile multi-gateway --project-name drawgame-prod-validation down
```

## L. Acceptance DEP-001 → DEP-030

| ID | Kỳ vọng | Kết quả thực tế | Trạng thái |
|---|---|---|---|
| DEP-001 | Production Compose parse | `config --quiet` thành công với env template | PASS |
| DEP-002 | Chỉ Caddy publish HTTP/HTTPS | JSON config chỉ Caddy có port 80/443 | PASS |
| DEP-003 | PostgreSQL không có host port | Không có binding | PASS |
| DEP-004 | Redis không có host port | Không có binding | PASS |
| DEP-005 | Gateway ports private | 8080 nội bộ; không publish 8080/8090 | PASS |
| DEP-006 | Room/Game/Chat ports private | HTTP/gRPC nội bộ, không host binding | PASS |
| DEP-007 | Frontend qua reverse proxy | HTTP `/` trả 200, browser kết nối ứng dụng | PASS |
| DEP-008 | SPA refresh | Đường dẫn SPA thử nghiệm trả 200 từ Nginx fallback | PASS |
| DEP-009 | WebSocket qua reverse proxy | Kết nối WebSocket qua Caddy thành công; production route `/ws` | PASS* |
| DEP-010 | WebSocket Upgrade | Handshake mở thành công qua Caddy; native client nhận kết nối | PASS* |
| DEP-011 | Gateway 1 nhận kết nối | APP_PONG trả `gateway-1` | PASS |
| DEP-012 | Gateway 2 nhận kết nối | APP_PONG trả `gateway-2` | PASS |
| DEP-013 | Cross-Gateway room event | Join/ready/chat broadcast giữa các client qua Caddy đạt | PASS |
| DEP-014 | Cross-Gateway drawing | DRAW_START/DRAW_BATCH tới Gateway còn lại; canvas stream khôi phục | PASS |
| DEP-015 | Reconnect qua Caddy | Client nối lại và resume phiên qua Gateway còn sống | PASS |
| DEP-016 | Stop Gateway 1 → failover | Gateway 1 dừng; Caddy đưa kết nối mới tới Gateway 2 | PASS |
| DEP-017 | Session resume | `SESSION_RESUMED` qua Gateway 2 | PASS |
| DEP-018 | Canvas recovery | Stroke round 2 còn trong `GET_CANVAS_STATE` sau failover | PASS |
| DEP-019 | PostgreSQL tồn tại sau Compose restart | Probe row còn sau `docker compose restart postgres` | PASS |
| DEP-020 | Không secret thật trong Git | Không thấy key/credential production trong tracked files | PASS |
| DEP-021 | JWT secret lấy từ env | Compose production bắt buộc `GAME_SESSION_JWT_SECRET` | PASS |
| DEP-022 | Postgres password lấy từ env | Compose production bắt buộc `POSTGRES_PASSWORD` | PASS |
| DEP-023 | Origin HTTPS cấu hình được | Env bắt buộc; browser thử với origin strict khớp | PASS |
| DEP-024 | Development không bị phá | Unit test/lint đạt; stack dev ban đầu tiếp tục chạy | PASS |
| DEP-025 | Direct multi-Gateway dev còn hỗ trợ | Profile và endpoint pool 8080/8090 giữ nguyên; frontend tests/build đạt | PASS |
| DEP-026 | Restart policy phù hợp | Tất cả container `unless-stopped`; process stop/start được thử, EC2 reboot chưa thử | PASS* |
| DEP-027 | Caddy certificate volumes persistent | `caddy-data` và `caddy-config` tồn tại sau `down` | PASS |
| DEP-028 | `down` không xóa DB volume | `down` không `-v`; volume PostgreSQL vẫn tồn tại | PASS |
| DEP-029 | Log rotation | Toàn bộ services có 10 MiB × 3 file | PASS |
| DEP-030 | Basic game smoke | Tạo/join/ready/start/vẽ/đoán/chat/round/reconnect/rematch đều đạt 12/12 | PASS |

`PASS*`: kiểm thử local chứng minh route/Upgrade trên HTTP overlay; WSS certificate thật và reboot EC2 chỉ có thể xác nhận sau khi DNS/Security Group/instance production sẵn sàng.

## M. Rà soát hardcode

- `localhost`, `127.0.0.1`, `ws://`: còn trong Compose/Dockerfile mặc định phát triển, fallback khi chạy Vite local, unit/E2E/load tests và hướng dẫn local. Đây là dev/test/documentation; không bị thay hàng loạt.
- Compose production dùng service DNS nội bộ; Caddy domain lấy từ `APP_DOMAIN`; production frontend dùng origin hiện tại của browser.
- Credential mẫu/dev fallback nằm trong cấu hình phát triển hoặc template test. Production Compose dùng mandatory env interpolation; không tìm thấy production domain/IP thật hay secret thật được commit.
- Không phát hiện production runtime path nào cần dùng localhost hoặc `ws://` cố định.

## N. File tạo/sửa cho phần chuẩn bị AWS

- Tạo `docker-compose.prod.yml`: override production, port isolation, network, memory/log limits, Caddy.
- Tạo `docker-compose.http-test.yml`: overlay HTTP-only, bind cục bộ được giới hạn loopback.
- Tạo `infra/caddy/Caddyfile`, `infra/caddy/Caddyfile.http`: cấu hình HTTPS cuối và thử IP tạm.
- Tạo `.env.production.example`: tên biến và placeholder an toàn.
- Tạo `.dockerignore`, cập nhật `frontend/.dockerignore`: loại secrets/cache/build artifacts khỏi build context.
- Cập nhật `.gitignore`: bỏ qua env production và PEM.
- Sửa `frontend/Dockerfile`, `frontend/src/websocket/WebSocketClient.ts`: bật lựa chọn same-origin cho production, giữ endpoint dev.
- Tạo `docs/aws-ec2-deployment.md`: hướng dẫn EC2, DNS/EIP, TLS, chạy/kiểm tra/cập nhật/dừng.
- Cập nhật `README.md`: liên kết ngắn tới hướng dẫn triển khai.
- Tạo `tools/e2e/production-proxy-smoke.mjs`: smoke test Caddy/gateway/canvas/failover/rematch; failover chỉ chạy khi có opt-in và tên Compose project chứa `validation`.
- Cập nhật `tools/e2e/ws-e2e.mjs` để non-host gửi `SET_READY` theo giao thức hiện tại.

Các thay đổi gameplay/UI và test chưa liên quan vốn đã có trong working tree trước phần chuẩn bị này được giữ nguyên.

## O. Giới hạn còn lại

- Không tạo/chỉnh EC2, Security Group, Elastic IP hoặc DNS; không gọi AWS và không deploy.
- Chưa chứng minh Caddy nhận certificate cho domain thật; cần DNS A trỏ Elastic IP và TCP 80/443 mở trước lần khởi chạy HTTPS.
- Chưa reboot EC2 thật. `unless-stopped` được xác nhận trong Compose config; kiểm tra sau reboot vẫn là bước triển khai.
- Tải production chưa benchmark; giới hạn RAM cần theo dõi trên t3.large.
- Bộ `tools/e2e/ws-e2e.mjs` tổng hợp vẫn có 14/65 assertion fail sau khi cập nhật Ready. Các case còn lại chứa giả định cũ (ví dụ chưa chọn từ word-selection trước khi vẽ, kỳ vọng secret word/timestamp/status cleanup ngay lập tức); chưa chẩn đoán chúng như lỗi deploy hay sửa gameplay trong task này. Smoke tests và hai QA suite khác đã đạt, nhưng các assertion cũ cần một đợt rà soát riêng.
- Một EC2 vẫn là single point of failure.

## P. Lệnh EC2 chuẩn sau khi clone

Sau khi điền `.env.production`, cấu hình DNS A trỏ Elastic IP và cho phép TCP 80/443 trong Security Group:

```bash
docker compose \
  --env-file .env.production \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  --profile multi-gateway \
  --parallel 2 \
  up -d --build
```

Không dùng `docker-compose.http-test.yml` trong cấu hình production cuối.

## Q. Sẵn sàng cho AWS?

**YES — sẵn sàng clone và chạy mà không sửa source thủ công**, sau khi chủ dự án điền secret/domain vào `.env.production`, DNS trỏ đúng Elastic IP và Security Group cho phép 80/443 (22 giới hạn IP quản trị). Phần cấp TLS thật, reboot instance và quan sát tải production vẫn phải xác minh trên EC2; task này không thực hiện các thao tác đó.
