import React, { useEffect } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  useNavigate,
  useLocation,
} from "react-router-dom";
import { WebSocketProvider } from "../websocket/WebSocketProvider";
import { useRoomStore } from "../store/roomStore";
import { HomePage } from "../pages/HomePage";
import { LobbyPage } from "../pages/LobbyPage";
import { GamePage } from "../pages/GamePage";
import { ErrorMessage } from "../components/ErrorMessage";
import { GameSystemNotice } from "../components/GameSystemNotice";

/**
 * Sync URL with room/game state so the browser address bar reflects the
 * actual screen the user is looking at (/  /lobby  /game).
 */
const RouteSync: React.FC = () => {
  const { room, isInRoom } = useRoomStore((s) => s);
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    let target = "/";
    if (isInRoom && room) {
      if (room.status === "LOBBY" || room.status === "WAITING") {
        target = "/lobby";
      } else {
        target = "/game";
      }
    }
    if (location.pathname !== target) {
      navigate(target, { replace: true });
    }
  }, [isInRoom, room?.status]);

  return null;
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
        <RouteSync />
        <Routes>
          <Route path="/lobby" element={<AppContent />} />
          <Route path="/game" element={<AppContent />} />
          <Route path="*" element={<AppContent />} />
        </Routes>
        <ErrorMessage />
      </WebSocketProvider>
    </BrowserRouter>
  );
};
