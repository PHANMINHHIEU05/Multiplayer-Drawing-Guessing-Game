import { describe, it, expect } from "vitest";
import { renderToString } from "react-dom/server";
import { PlayerList } from "./PlayerList";
import { Player } from "../types/room";

describe("PlayerList", () => {
  const mockPlayers: Player[] = [
    { playerId: "p-1", username: "Minh", ready: true, connected: true },
    { playerId: "p-2", username: "An", ready: false, connected: false },
  ];

  it("renders host badge, current user tag, and readiness badges", () => {
    const html = renderToString(
      <PlayerList
        players={mockPlayers}
        hostPlayerId="p-1"
        currentPlayerId="p-1"
        canKick={true}
      />,
    );

    // Host badge
    expect(html).toContain("👑 Chủ phòng");
    // Current user tag
    expect(html).toContain("Bạn");
    // Unready player badge
    expect(html).toContain("Chưa sẵn sàng");
    // Connected status
    expect(html).toContain("Đã kết nối");
    // Disconnected status
    expect(html).toContain("Đang kết nối lại...");
    // Contextual kick for non-host
    expect(html).toContain("Mời ra");
  });

  it("does not render kick button on host or when canKick is false", () => {
    const html = renderToString(
      <PlayerList
        players={mockPlayers}
        hostPlayerId="p-1"
        currentPlayerId="p-2"
        canKick={false}
      />,
    );

    expect(html).not.toContain("Mời ra");
  });
});
