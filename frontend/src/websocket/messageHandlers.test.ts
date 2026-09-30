import { afterEach, describe, expect, it, vi } from "vitest";
import { setupMessageHandlers } from "./messageHandlers";
import { MessageType } from "./protocol";
import { gameStore } from "../store/gameStore";
import { playerStore } from "../store/playerStore";
import { roomStore } from "../store/roomStore";
import { guessStore } from "../store/guessStore";

const { send } = vi.hoisted(() => ({ send: vi.fn() }));
vi.mock("./WebSocketClient", () => ({ wsClient: { send } }));

describe("GAME_STARTED viewer state refresh", () => {
  afterEach(() => {
    send.mockReset();
    send.mockResolvedValue({});
    gameStore.clearGame();
    roomStore.clearRoom();
    guessStore.clearGuesses();
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

describe("PLAYER_GUESSED_CORRECTLY display name", () => {
  afterEach(() => {
    roomStore.clearRoom();
    guessStore.clearGuesses();
  });

  it("resolves the nickname from the room instead of displaying the internal player id", () => {
    roomStore.setRoom({
      roomId: "ROOM1",
      status: "PLAYING",
      hostPlayerId: "player_host",
      players: [
        { playerId: "player_host", username: "Chủ phòng" },
        { playerId: "player_abc123", username: "Minh" },
      ],
      maxPlayers: 4,
      roundCount: 3,
      roundDuration: 60,
      playerCount: 2,
      selectedCategories: ["ANIMALS"],
    });

    setupMessageHandlers()({
      type: MessageType.PLAYER_GUESSED_CORRECTLY,
      roomId: "ROOM1",
      playerId: "player_abc123",
      scoreAwarded: 100,
    });

    expect(guessStore.getState().guesses).toHaveLength(1);
    expect(guessStore.getState().guesses[0].username).toBe("Minh");
  });
});

describe("PLAYER_LEFT for the current player", () => {
  afterEach(() => {
    playerStore.clearSessionToken();
    roomStore.clearRoom();
    gameStore.clearGame();
    guessStore.clearGuesses();
  });

  it("clears stale room membership so a fast reconnect cannot keep the player inside", () => {
    playerStore.setPlayer("Dũng 2", "player-2");
    playerStore.setSessionToken("stale-resume-token");
    roomStore.setRoom({
      roomId: "ROOM1",
      status: "PLAYING",
      hostPlayerId: "player-1",
      players: [
        { playerId: "player-1", username: "Dũng" },
        { playerId: "player-2", username: "Dũng 2" },
      ],
      maxPlayers: 4,
      roundCount: 3,
      roundDuration: 60,
      playerCount: 2,
      selectedCategories: ["ANIMALS"],
    });

    setupMessageHandlers()({
      type: MessageType.PLAYER_LEFT,
      roomId: "ROOM1",
      playerId: "player-2",
      username: "Dũng 2",
      players: [{ playerId: "player-1", username: "Dũng" }],
    });

    expect(roomStore.getState().room).toBeNull();
    expect(playerStore.getSessionToken()).toBeNull();
    expect(gameStore.getState().gameState).toBeNull();
  });
});

describe("DRAW_EVENT fill synchronization", () => {
  afterEach(() => {
    gameStore.clearGame();
  });

  it("preserves a remote paint-bucket operation instead of converting it to a brush point", () => {
    setupMessageHandlers()({
      type: MessageType.DRAW_EVENT,
      roomId: "ROOM1",
      point: {
        x: 0.25,
        y: 0.75,
        color: "#ef4444",
        size: 1,
        isNewPath: true,
        tool: "FILL",
      },
    });

    expect(gameStore.getState().drawPoints).toEqual([
      expect.objectContaining({
        x: 0.25,
        y: 0.75,
        color: "#ef4444",
        tool: "FILL",
      }),
    ]);
  });
});
