import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { pingStore } from "./pingStore";

describe("pingStore", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    pingStore.clear();
    vi.useRealTimers();
  });

  const basePing = {
    playerId: "player-1",
    displayName: "Minh",
    gameId: "game-1",
    roundNumber: 2,
    pingType: "DANGER",
    x: 0.25,
    y: 0.75,
  };

  it("adds a ping preserving its type and normalized coordinates", () => {
    pingStore.add(basePing);

    const [ping] = pingStore.getState();
    expect(ping.x).toBe(0.25);
    expect(ping.y).toBe(0.75);
    expect(ping.playerId).toBe("player-1");
    expect(ping.pingType).toBe("DANGER");
    expect(ping.id).toBeTruthy();
  });

  it("expires pings after their ttl", () => {
    pingStore.add(basePing);
    expect(pingStore.getState()).toHaveLength(1);

    vi.advanceTimersByTime(2500);

    expect(pingStore.getState()).toHaveLength(0);
  });

  it("caps the number of concurrent pings", () => {
    for (let i = 0; i < 15; i += 1) {
      pingStore.add({ ...basePing, playerId: `player-${i}` });
    }

    expect(pingStore.getState()).toHaveLength(12);
    expect(pingStore.getState()[0].playerId).toBe("player-3");
  });

  it("clear() removes every ping at once", () => {
    pingStore.add(basePing);
    pingStore.add({ ...basePing, playerId: "player-2" });

    pingStore.clear();

    expect(pingStore.getState()).toHaveLength(0);
  });
});
