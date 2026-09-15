#!/usr/bin/env bash
#
# TV10 — REAL GATEWAY FAILOVER DEMO
#
# Demonstrates application-level failover: the browser client (built with
# VITE_WS_URLS endpoint pool) is connected to gateway-1; we KILL gateway-1 and
# watch the client reconnect through gateway-2 with the same JWT identity,
# room/game/canvas state restored via RESUME_SESSION + GET_CANVAS_STATE.
#
# Safe chaos action: `docker stop` on ONE gateway container only.
# NEVER removes volumes; Redis/PostgreSQL/domain services stay running.
#
# Usage:
#   ./tools/chaos/failover-demo.sh           # stop gateway-1, watch, restart it
#   ./tools/chaos/failover-demo.sh --no-restart   # leave gateway-1 stopped
#
# Requires: docker compose --profile multi-gateway up -d --build (TV10 images)
#           frontend at http://localhost:3000 (VITE_WS_URLS pool baked in)

set -euo pipefail

GATEWAY_1="drawgame-realtime-gateway"
GATEWAY_2="drawgame-realtime-gateway-2"
RESTART=1
[[ "${1:-}" == "--no-restart" ]] && RESTART=0

say() { printf '\n\033[1;36m=== %s ===\033[0m\n' "$1"; }

say "1. Kiểm tra stack"
for c in "$GATEWAY_1" "$GATEWAY_2" drawgame-redis drawgame-game-service drawgame-room-service; do
  state=$(docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null || echo "missing")
  echo "  $c: $state"
  [[ "$state" == "running" ]] || { echo "❌ Stack chưa sẵn sàng — chạy: docker compose --profile multi-gateway up -d --build"; exit 1; }
done
echo "✅ Cả 2 gateway + domain services đang chạy."

say "2. Kịch bản demo (làm theo UI trong trình duyệt)"
cat <<'EOF'
  a. Mở 2+ cửa sổ trình duyệt (http://localhost:3000), tạo phòng + join
  b. Host READY hết người rồi START GAME
  c. Để drawer vẽ vài nét — mọi người thấy nét vẽ đồng bộ
  d. Quan sát Network Inspector (chip ⚡ NET) hiện gateway-1

  >>> SẴN SÀNG? Nhấn Enter để KILL gateway-1 <<<
EOF
read -r

say "3. KILL gateway-1 (docker stop — an toàn, không đụng volumes)"
docker stop "$GATEWAY_1"
echo "💥 gateway-1 đã dừng lúc $(date +%T)"

say "4. Quan sát trong trình duyệt"
cat <<'EOF'
  Kỳ vọng trong ~2-5 giây:
  - Banner: "Mất kết nối..." → "Đang chuyển máy chủ..." (FAILING_OVER)
  - Tự động reconnect qua gateway-2 (endpoint pool xoay vòng)
  - Banner: "Đang khôi phục ván chơi..." (RECOVERING — canvas recovery)
  - Network Inspector: gateway-1 → gateway-2 ✅
  - Ván chơi tiếp tục: nét vẽ đã vẽ trong lúc đứt kết nối được khôi phục
    (drawer vẫn vẽ được nếu drawer ở gateway-2; hoặc chờ drawer reconnect)
EOF

say "5. Chờ quan sát (30s)"
sleep 30

if [[ $RESTART -eq 1 ]]; then
  say "6. Khởi động lại gateway-1"
  docker start "$GATEWAY_1"
  echo "🔄 gateway-1 trở lại lúc $(date +%T) — client hiện tại vẫn ở gateway-2 (không reconnect storm)."
  echo "   (Client chỉ quay lại gateway-1 nếu gateway-2 cũng chết — newest-resume-wins giữ identity.)"
fi

say "7. Sức khoẻ hệ thống"
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep drawgame
echo
echo "✅ Demo failover hoàn tất. Kiểm tra log nếu cần:"
echo "   docker logs drawgame-realtime-gateway-2 2>&1 | grep -E 'RESUME_SESSION|DrawingRoomStateCache'"
