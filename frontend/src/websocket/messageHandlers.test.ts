import { afterEach, describe, expect, it, vi } from "vitest";
import { setupMessageHandlers } from "./messageHandlers";
import { MessageType } from "./protocol";
import { gameStore } from "../store/gameStore";
import { playerStore } from "../store/playerStore";
import { roomStore } from "../store/roomStore";

const { send } = vi.hoisted(() => ({ send: vi.fn() }));
vi.mock("./WebSocketClient", () => ({ wsClient: { send } }));

describe("GAME_STARTED viewer state refresh", () => {
  afterEach(() => {
    send.mockReset();
    send.mockResolvedValue({});
    gameStore.clearGame();
    roomStore.clearRoom();
  });

  it("immediately fetches drawer-private choices instead of waiting for the polling interval", () => {
    playerStore.setPlayer("Drawer", "drawer-1");
    send.mockResolvedValue({});

    setupMessageHandlers()({
      type: MessageType.GAME_STARTED,
      roomId: "ROOM1",
      gameId: "match-1",
      status: "PLAYING",
      currentRound: 1,
      totalRounds: 5,
      drawerId: "drawer-1",
      roundPhase: "WORD_SELECTION",
      phaseEndsAt: Date.now() + 10_000,
      wordChoices: [],
      scores: [],
    });

    expect(gameStore.getState().gameState?.wordChoices).toEqual([]);
    expect(send).toHaveBeenCalledWith(
      MessageType.GET_GAME_STATE,
      { roomId: "ROOM1", playerId: "drawer-1" },
      5000,
    );
  });
});
