import React, { useEffect } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  useNavigate,
  useLocation,
  useParams,
  Navigate,
} from "react-router-dom";
import { WebSocketProvider } from "../websocket/WebSocketProvider";
import { wsClient, resetAllSessionState } from "../websocket/WebSocketClient";
import { useRoomStore, roomStore } from "../store/roomStore";
import { useConnectionStore } from "../store/connectionStore";
import { HomePage } from "../pages/HomePage";
import { LobbyPage } from "../pages/LobbyPage";
import { GamePage } from "../pages/GamePage";
import { RoomRestoringView } from "../components/RoomRestoringView";
import { ErrorMessage } from "../components/ErrorMessage";
import { GameSystemNotice } from "../components/GameSystemNotice";
import { MatchRecapModal } from "../components/MatchRecapModal";

/**
 * Đồng bộ thanh địa chỉ trình duyệt với trạng thái phòng hiện tại
 * (/lobby/<id> hoặc /game/<id>) khi người chơi đang ở trong phòng.
 */
const RouteSync: React.FC = () => {
  const { room, isInRoom } = useRoomStore((s) => s);
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (isInRoom && room?.roomId) {
      const isLobby = room.status === "LOBBY" || room.status === "WAITING";
      const target = isLobby ? `/lobby/${room.roomId}` : `/game/${room.roomId}`;
      if (location.pathname !== target) {
        navigate(target, { replace: true });
      }
    }
  }, [isInRoom, room?.roomId, room?.status, location.pathname, navigate]);

  return null;
};

/**
 * Route cho phòng (cả lobby và game):
 * - Nếu đã có room trong store khớp với URL: hiển thị LobbyPage hoặc GamePage.
 * - Nếu vừa F5 (chưa có room trong store): hiển thị màn hình chờ và tự động khôi phục.
 */
export const RoomRoute: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const normalizedRoomId = (roomId || "").trim().toUpperCase();
  const { room, isInRoom, isRestoring, restoreError } = useRoomStore((s) => s);
  const connectionStatus = useConnectionStore((s) => s.status);
  const navigate = useNavigate();

  useEffect(() => {
    if (!normalizedRoomId) return;

    // Đã ở đúng phòng rồi
    if (isInRoom && room?.roomId === normalizedRoomId) {
      return;
    }

    // Nếu WebSocket đã kết nối và chưa trong quá trình khôi phục: kích hoạt khôi phục session
    if (connectionStatus === "CONNECTED" && !isRestoring) {
      void wsClient.restoreStateAfterReconnect(normalizedRoomId);
    }
  }, [normalizedRoomId, isInRoom, room?.roomId, connectionStatus, isRestoring]);

  // Nếu người chơi đã ở trong phòng khớp với roomId trên URL
  if (isInRoom && room && room.roomId === normalizedRoomId) {
    if (room.status === "LOBBY" || room.status === "WAITING") {
      return <LobbyPage />;
    }
    return <GamePage />;
  }

  // Màn hình loading chờ kết nối lại phòng sau F5 hoặc báo lỗi nếu không tìm thấy phòng
  return (
    <RoomRestoringView
      roomId={normalizedRoomId}
      isRestoring={isRestoring || connectionStatus !== "CONNECTED"}
      error={restoreError}
      onGoHome={() => {
        resetAllSessionState();
        navigate("/", { replace: true });
      }}
    />
  );
};

const LobbyRedirect: React.FC = () => {
  const { room, isInRoom } = useRoomStore((s) => s);
  const lastRoomId = roomStore.getLastRoomId();

  if (isInRoom && room?.roomId) {
    return <Navigate to={`/lobby/${room.roomId}`} replace />;
  }
  if (lastRoomId) {
    return <Navigate to={`/lobby/${lastRoomId}`} replace />;
  }
  return <Navigate to="/" replace />;
};

const GameRedirect: React.FC = () => {
  const { room, isInRoom } = useRoomStore((s) => s);
  const lastRoomId = roomStore.getLastRoomId();

  if (isInRoom && room?.roomId) {
    return <Navigate to={`/game/${room.roomId}`} replace />;
  }
  if (lastRoomId) {
    return <Navigate to={`/game/${lastRoomId}`} replace />;
  }
  return <Navigate to="/" replace />;
};

export const AppContent: React.FC = () => {
  const { room, isInRoom } = useRoomStore((s) => s);

  if (!isInRoom || !room) {
    return <HomePage />;
  }

  if (room.status === "LOBBY" || room.status === "WAITING") {
    return <LobbyPage />;
  }

  return <GamePage />;
};

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <WebSocketProvider>
        <GameSystemNotice />
        <MatchRecapModal />
        <RouteSync />
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/lobby/:roomId" element={<RoomRoute />} />
          <Route path="/game/:roomId" element={<RoomRoute />} />
          <Route path="/lobby" element={<LobbyRedirect />} />
          <Route path="/game" element={<GameRedirect />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        <ErrorMessage />
      </WebSocketProvider>
    </BrowserRouter>
  );
};
