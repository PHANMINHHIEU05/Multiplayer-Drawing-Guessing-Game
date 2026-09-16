import React from "react";
import { WebSocketProvider } from "../websocket/WebSocketProvider";
import { useRoomStore } from "../store/roomStore";
import { HomePage } from "../pages/HomePage";
import { LobbyPage } from "../pages/LobbyPage";
import { GamePage } from "../pages/GamePage";
import { ErrorMessage } from "../components/ErrorMessage";
import { GameSystemNotice } from "../components/GameSystemNotice";

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
    <WebSocketProvider>
      <GameSystemNotice />
      <AppContent />
      <ErrorMessage />
    </WebSocketProvider>
  );
};
