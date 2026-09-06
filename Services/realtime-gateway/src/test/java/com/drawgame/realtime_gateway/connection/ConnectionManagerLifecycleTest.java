package com.drawgame.realtime_gateway.connection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TV3 Stabilization — Tests for connection lifecycle fixes.
 *
 * Covers:
 *  GW-07: LEAVE_ROOM unbinds session from room
 *  GW-09: disconnect cleanup (via remove)
 *  GW-10: reconnect with new session evicts old duplicate routing state
 *  GW-14: repeated connect/disconnect does not accumulate dead entries
 */
class ConnectionManagerLifecycleTest {

    private ConnectionManager manager;

    @BeforeEach
    void setUp() {
        manager = new ConnectionManager();
    }

    // ── GW-07: unbindSession after LEAVE_ROOM ─────────────────────────────────

    @Test
    void unbindSession_clearsRoomAndPlayerMappings() {
        manager.register("session-1");
        manager.bindSession("session-1", "room-A", "player-1");

        assertThat(manager.getRoomId("session-1")).isEqualTo("room-A");
        assertThat(manager.getPlayerId("session-1")).isEqualTo("player-1");

        manager.unbindSession("session-1");

        assertThat(manager.getRoomId("session-1")).isNull();
        assertThat(manager.getPlayerId("session-1")).isNull();
    }

    @Test
    void unbindSession_doesNotCloseConnection_sessionStillRegistered() {
        manager.register("session-1");
        manager.bindSession("session-1", "room-A", "player-1");

        manager.unbindSession("session-1");

        // WebSocket connection must still be registered (not removed)
        assertThat(manager.getOnlineCount()).isEqualTo(1);
    }

    @Test
    void unbindSession_afterLeave_drawingAuthorizationWillFail() {
        // After unbind, getRoomId returns null → DrawingAuthorizationService will reject as NO_ROOM
        manager.register("session-1");
        manager.bindSession("session-1", "room-A", "player-1");
        manager.unbindSession("session-1");

        assertThat(manager.getRoomId("session-1")).isNull();
    }

    // ── GW-09: remove cleans up dead connection fully ─────────────────────────

    @Test
    void remove_decrements_onlineCount() {
        manager.register("session-1");
        manager.bindSession("session-1", "room-A", "player-1");

        assertThat(manager.getOnlineCount()).isEqualTo(1);

        manager.remove("session-1");

        assertThat(manager.getOnlineCount()).isEqualTo(0);
    }

    @Test
    void remove_clearsAllMappings() {
        manager.register("session-1");
        manager.bindSession("session-1", "room-A", "player-1");

        manager.remove("session-1");

        assertThat(manager.getRoomId("session-1")).isNull();
        assertThat(manager.getPlayerId("session-1")).isNull();
        assertThat(manager.getQueueForSession("session-1")).isNull();
    }

    // ── GW-10: duplicate session eviction on reconnect ────────────────────────

    @Test
    void bindSession_evictsStaleRoutingState_whenSamePlayerReconnects() {
        manager.register("old-session");
        manager.bindSession("old-session", "room-A", "player-1");

        // Player reconnects with a new session
        manager.register("new-session");
        manager.bindSession("new-session", "room-A", "player-1");

        // Old session's routing state must be evicted
        assertThat(manager.getRoomId("old-session")).isNull();
        assertThat(manager.getPlayerId("old-session")).isNull();

        // New session must be correctly bound
        assertThat(manager.getRoomId("new-session")).isEqualTo("room-A");
        assertThat(manager.getPlayerId("new-session")).isEqualTo("player-1");
    }

    @Test
    void bindSession_differentRoom_doesNotEvict() {
        manager.register("session-1");
        manager.bindSession("session-1", "room-A", "player-1");

        // Same player joins a DIFFERENT room (valid multi-room scenario)
        manager.register("session-2");
        manager.bindSession("session-2", "room-B", "player-1");

        // Both sessions still have their own room bindings (different rooms → different keys)
        assertThat(manager.getRoomId("session-1")).isEqualTo("room-A");
        assertThat(manager.getRoomId("session-2")).isEqualTo("room-B");
    }

    @Test
    void bindSession_samePLayerSameSession_isIdempotent() {
        manager.register("session-1");
        manager.bindSession("session-1", "room-A", "player-1");
        // Calling bindSession again with same session must not evict itself
        manager.bindSession("session-1", "room-A", "player-1");

        assertThat(manager.getRoomId("session-1")).isEqualTo("room-A");
        assertThat(manager.getPlayerId("session-1")).isEqualTo("player-1");
    }

    // ── GW-14: repeated connect/disconnect — no infinite growth ───────────────

    @Test
    void repeatedConnectDisconnect_doesNotAccumulateDeadEntries() {
        for (int i = 0; i < 20; i++) {
            String sessionId = "session-" + i;
            manager.register(sessionId);
            manager.bindSession(sessionId, "room-A", "player-" + i);
            manager.remove(sessionId);
        }

        assertThat(manager.getOnlineCount()).isEqualTo(0);
    }

    @Test
    void repeatedReconnectSamePlayer_onlyOneActiveEntry() {
        // Simulate 5 reconnects of the same player
        for (int i = 0; i < 5; i++) {
            String sessionId = "session-reconnect-" + i;
            manager.register(sessionId);
            manager.bindSession(sessionId, "room-A", "player-persistent");
        }

        // Only 5 sessions are registered (old sessions' WS connections are separate — not removed by routing eviction)
        assertThat(manager.getOnlineCount()).isEqualTo(5);

        // But only the latest session has active room routing
        int boundSessions = 0;
        for (int i = 0; i < 5; i++) {
            if (manager.getRoomId("session-reconnect-" + i) != null) {
                boundSessions++;
            }
        }
        // Only the last session (index 4) should have room routing
        assertThat(boundSessions).isEqualTo(1);
        assertThat(manager.getRoomId("session-reconnect-4")).isEqualTo("room-A");
    }
}
