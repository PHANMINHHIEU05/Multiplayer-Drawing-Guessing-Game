# Triển khai production trên AWS EC2

> Tài liệu này triển khai toàn bộ stack trên một EC2 Ubuntu Server 24.04 LTS amd64 bằng Docker Compose. Caddy tiếp nhận HTTP/HTTPS, tự cấp và gia hạn chứng chỉ, phục vụ frontend và proxy WebSocket đến hai Realtime Gateway. Hướng dẫn không tạo hay thay đổi tài nguyên AWS.

## 1. Sơ đồ và phạm vi

```text
Internet
  ├── HTTPS/WSS :443 ─┐
  └── HTTP      :80  ─┴─> Caddy ──> frontend (Nginx, SPA fallback)
                           └──────> realtime-gateway :8080
                                      realtime-gateway-2 :8080
                                          ├── gRPC -> room-service :9091
                                          ├── gRPC -> game-service :9092
                                          └── gRPC -> chat-service :9093
                                               ├── Redis :6379
                                               └── PostgreSQL :5432
```

Tất cả service chạy trên một EC2. Caddy nối vào mạng nội bộ và mạng edge; các service còn lại chỉ nối vào mạng `drawing-game-internal`. Compose service DNS được dùng thay cho IP cố định. Caddy phân phối kết nối WebSocket mới luân phiên giữa `realtime-gateway` và `realtime-gateway-2`; kết nối WebSocket đang mở sẽ đóng nếu Gateway đang giữ socket chết. Frontend tự kết nối lại cùng origin, sau đó JWT/session resume và canvas recovery tiếp tục theo cơ chế hiện có. Không cấu hình sticky session.

Một EC2 không phải HA ở cấp hạ tầng: nếu host hoặc vùng lỗi, cả hai Gateway và các service cùng dừng. Hai Gateway chỉ giảm ảnh hưởng khi một tiến trình Gateway lỗi.

## 2. Yêu cầu máy chủ và mạng AWS

- Ubuntu Server 24.04 LTS, amd64/x86_64; máy hiện tại t3.large, 2 vCPU và 8 GiB RAM.
- Docker Engine và Docker Compose plugin đã cài.
- Security Group chỉ cho phép `22/tcp` từ IP quản trị và `80/tcp`, `443/tcp` từ Internet. Không mở cổng ứng dụng hoặc dữ liệu.
- Tên miền phải có bản ghi A trỏ đến Elastic IP trước khi Caddy cấp chứng chỉ HTTPS. Có thể kiểm tra IP bằng cấu hình HTTP tạm ở mục 5.

Kiểm tra trên EC2:

```bash
lsb_release -ds
dpkg --print-architecture
docker --version
docker compose version
```

## 3. Clone và cấu hình bí mật

Với repository riêng, dùng SSH/deploy key đã được giới hạn quyền hoặc cơ chế đăng nhập Git có credential manager. Không dán GitHub token dài hạn vào URL lệnh vì shell history và process list có thể lưu lại.

```bash
git clone <REPOSITORY_SSH_URL>
cd Multiplayer-Drawing-Guessing-Game
cp .env.production.example .env.production
chmod 600 .env.production
openssl rand -base64 48
openssl rand -base64 36
nano .env.production
```

Thay các giá trị sau trong `.env.production`:

- `APP_DOMAIN`: domain thật, ví dụ `game.example.com`.
- `WS_ALLOWED_ORIGINS`: đúng origin HTTPS, ví dụ `https://game.example.com`, không có dấu `/` cuối.
- `POSTGRES_PASSWORD`: mật khẩu ngẫu nhiên riêng.
- `GAME_SESSION_JWT_SECRET`: output từ `openssl rand -base64 48`. Cùng một giá trị được dùng cho cả hai Gateway.

Giữ `POSTGRES_DB`, `POSTGRES_USER` và thời hạn token theo giá trị phù hợp. File `.env.production` bị Git ignore; không commit file này. Compose từ chối chạy production nếu thiếu domain, password, JWT secret hoặc allowlist Origin. Không dùng fallback dev secret/password trong production.

Frontend production tự tạo endpoint WebSocket cùng origin: trang HTTPS dùng `wss://<host>/ws`, trang HTTP dùng `ws://<host>/ws`. URL này được quyết định ở trình duyệt, do đó có thể dùng IP trong bài kiểm tra HTTP mà không bake domain vào bundle. Cấu hình `VITE_WS_URLS` nhiều Gateway của môi trường dev vẫn giữ nguyên.

## 4. DNS, TLS và chạy production

Trong nhà cung cấp DNS (Route 53, Cloudflare hoặc nhà cung cấp khác), tạo bản ghi:

| Tên | Loại | Giá trị |
|---|---|---|
| `game.example.com` | A | `<Elastic-IP>` |

Nếu chưa có Elastic IP: AWS Console → EC2 → Elastic IPs → Allocate Elastic IP address → Associate Elastic IP address với instance. IPv4 public thông thường có thể đổi khi stop/start instance; Elastic IP giữ địa chỉ ổn định. Không ghi IP tạm vào cấu hình trong repository.

Sau khi DNS phân giải về instance và Security Group mở TCP 80/443, chạy từ thư mục repo:

```bash
docker compose \
  --env-file .env.production \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  --profile multi-gateway \
  --parallel 2 \
  up -d --build

docker compose \
  --env-file .env.production \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  --profile multi-gateway \
  ps
```

`--parallel 2` giới hạn số tác vụ build chạy đồng thời trên máy 2 vCPU/8 GiB. Caddy tự quản lý HTTPS certificate, HTTP→HTTPS redirect và renewal; volume `caddy-data` giữ chứng chỉ qua lần tạo lại container. Frontend được phục vụ qua Nginx hiện có với SPA fallback. Flyway migration của Game Service chạy khi ứng dụng khởi động; Hibernate dùng `ddl-auto: validate`. Trước khi triển khai thay đổi schema, tạo bản sao lưu PostgreSQL và kiểm tra migration mới.

## 5. Thử bằng IP trước khi DNS sẵn sàng

Cấu hình production cuối vẫn giữ HTTPS. Overlay riêng `docker-compose.http-test.yml` chỉ bật HTTP tạm trên cổng 80 và thay Caddyfile sang `:80`; nó không publish 443. Trên EC2, cổng này được publish công khai để truy cập bằng IP. Khi kiểm tra trên máy dev, đặt `HTTP_TEST_ADDRESS=127.0.0.1:18080` để chỉ bind loopback. Để thử đầy đủ WebSocket qua IP, tạm sửa trong `.env.production`:

```dotenv
WS_ALLOWED_ORIGINS=http://<EC2_PUBLIC_IP>
```

Thay placeholder bằng IPv4 thật của EC2, không thêm dấu `/`. Giữ `APP_DOMAIN` là domain dự kiến để Compose vẫn kiểm tra biến bắt buộc. Chạy:

```bash
docker compose \
  --env-file .env.production \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  -f docker-compose.http-test.yml \
  --profile multi-gateway \
  --parallel 2 \
  up -d --build

curl -I http://<EC2_PUBLIC_IP>/
```

Mở `http://<EC2_PUBLIC_IP>`; kiểm tra route SPA sau refresh và DevTools → Network → WS có kết nối `ws://<EC2_PUBLIC_IP>/ws` thành công. Sau đó đổi `WS_ALLOWED_ORIGINS` về `https://<APP_DOMAIN>`, bỏ `docker-compose.http-test.yml` khỏi lệnh, cấu hình DNS và chạy lệnh production ở mục 4. Không để origin HTTP/IP trong cấu hình cuối và không dùng overlay HTTP để phục vụ Internet lâu dài.

## 6. Kiểm tra vận hành

Các lệnh dùng chung các file cấu hình production:

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml --profile multi-gateway ps
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml --profile multi-gateway logs --tail=200
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml --profile multi-gateway logs -f realtime-gateway realtime-gateway-2 caddy
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml --profile multi-gateway stats
docker ps --format 'table {{.Names}}\t{{.Ports}}'
```

Trong `docker ps`, chỉ Caddy được phép hiện host binding `:80` và `:443`. PostgreSQL, Redis, frontend, Gateways và các cổng HTTP/gRPC backend không được hiện dạng `0.0.0.0:<port>->...` hoặc `[::]:<port>->...`. Có thể kiểm tra listener host bằng `sudo ss -lntp`.

### Kiểm tra ứng dụng

1. Mở `https://<APP_DOMAIN>` và tải lại một route SPA.
2. Mở hai trình duyệt/ẩn danh, tạo phòng, tham gia, Ready, bắt đầu ván, vẽ, đoán và chat.
3. Xác nhận cả hai Gateway đang chạy; kết nối mới được Caddy phân phối đến cả hai theo vòng.
4. Thử round tiếp theo, reconnect, rematch và vẽ từ client nối qua Gateway khác. Caddy health-check `/actuator/health` trên cả hai Gateway; khi Gateway không khỏe, kết nối mới được gửi đến upstream khỏe còn lại.
5. Dừng `realtime-gateway` trong lúc có client kết nối. Socket đó sẽ đóng; frontend reconnect qua Caddy và có thể đến Gateway còn sống. Xác nhận resume và canvas recovery. Không kỳ vọng socket đang mở không bị ngắt khi process dừng.
6. Khởi động lại stack rồi xác nhận dữ liệu PostgreSQL còn nguyên.

### Dừng, cập nhật, khởi động lại EC2

Dừng stack một cách an toàn (giữ các named volume):

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml --profile multi-gateway down
```

Không thêm `-v` nếu không chủ ý xóa volume. `docker compose down -v` xóa cả volume PostgreSQL, Redis và dữ liệu Caddy.

Cập nhật code và chạy lại stack:

```bash
git pull
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml --profile multi-gateway --parallel 2 up -d --build
```

Migration Flyway được áp dụng khi Game Service khởi động. Sao lưu DB trước bản cập nhật có migration; không xóa volume để xử lý migration lỗi.

Restart policy `unless-stopped` được cấu hình cho mọi container. Sau `sudo reboot`, chờ Docker khởi động rồi dùng lệnh `ps` và `logs` ở trên để xác nhận trạng thái. Compose không yêu cầu chạy lệnh `up` thủ công sau reboot.

## 7. Ma trận cổng

| Cổng | Công khai? | Chủ sở hữu | Ghi chú |
|---:|---|---|---|
| 22/tcp | Có, giới hạn IP quản trị | SSH / AWS Security Group | Không publish qua Compose |
| 80/tcp | Có | Caddy | HTTP và redirect HTTPS; HTTP test tạm dùng cổng này |
| 443/tcp | Có | Caddy | HTTPS/WSS và ACME TLS |
| 3000/tcp | Không | Frontend dev | Không publish trong production |
| 8080/tcp | Không | Gateway 1 | Chỉ trong Docker network |
| 8090/tcp | Không | Gateway 2 host mapping dev | Không publish trong production; container port vẫn là 8080 |
| 8081/tcp, 9091/tcp | Không | Room HTTP/Actuator, gRPC | Chỉ nội bộ |
| 8082/tcp, 9092/tcp | Không | Game HTTP/Actuator, gRPC | Chỉ nội bộ |
| 8083/tcp, 9093/tcp | Không | Chat HTTP/Actuator, gRPC | Chỉ nội bộ |
| 5432/tcp | Không | PostgreSQL | Named volume persistent |
| 6379/tcp | Không | Redis | AOF và named volume theo cấu hình hiện tại |

Trong mạng Compose, backend gọi `redis`, `postgres`, `room-service`, `game-service`, `chat-service`, `realtime-gateway` và `realtime-gateway-2` bằng DNS service. `EXPOSE` trong Dockerfile chỉ là metadata, không tự publish cổng.

## 8. Lưu ý bảo mật và vận hành

- Origin ở production phải khớp chính xác `https://<APP_DOMAIN>`; không dùng `*`.
- `.env.production` chứa credential, quyền file nên là `600`, và file đã được Git ignore. Rotate credential nếu từng bị lộ.
- Tất cả container dùng chính sách log `json-file`, tối đa 10 MiB mỗi file và 3 file/container.
- Java 21 nhận giới hạn bộ nhớ qua cgroup; các service dùng `MaxRAMPercentage=50` và `InitialRAMPercentage=10`. Giới hạn container lần lượt là Gateway 768 MiB mỗi instance, Room 512 MiB, Game 1 GiB, Chat 512 MiB. PostgreSQL 1 GiB, Redis 512 MiB, frontend 256 MiB, Caddy 256 MiB. Đây là giới hạn khởi đầu thận trọng; theo dõi `docker stats` trước khi điều chỉnh.
- PostgreSQL được health-check bằng `pg_isready`, Redis bằng `redis-cli ping`. Room/Game/Chat/Gateway có Actuator `/actuator/health`; Caddy chủ động kiểm tra hai Gateway mỗi 10 giây. `depends_on: service_started` chỉ chờ container bắt đầu, không khẳng định ứng dụng gRPC đã sẵn sàng.
- Redis tiếp tục dùng AOF và volume theo cấu hình hiện có. Pub/Sub vẫn là fan-out best-effort; Streams/canvas recovery vẫn theo thiết kế ứng dụng.
- File Compose production giữ Postgres/Redis/service/gateway ports nội bộ; chỉ Caddy publish 80/443. Điều này phụ thuộc cả vào việc Security Group chỉ mở các cổng được chỉ định.
