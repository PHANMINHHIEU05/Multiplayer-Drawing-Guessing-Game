package com.drawgame.realtime_gateway.drawing.routing;

import com.drawgame.realtime_gateway.drawing.protocol.ClearCanvasMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingProtocol;
import com.drawgame.realtime_gateway.drawing.protocol.DrawStartMessage;
import com.drawgame.realtime_gateway.drawing.transport.DrawingSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TV3 Stabilization — tests for round/drawer lifecycle correctness.
 *
 * Covers:
 *  GW-05: stale round batch is rejected after round transition
 *  GW-06: old drawer rejected, new drawer accepted after round change
 *  GW-13: drawing rejected when game is not PLAYING (finished)
 */
class DrawingLifecycleCacheTest {

    private DrawingRoomStateCache cache;
    private DrawingAuthorizationService authService;

    @BeforeEach
    void setUp() {
        cache = new DrawingRoomStateCache();
        authService = new DrawingAuthorizationService(cache);
    }

    private DrawingSessionContext session(String sessionId, String roomId, String playerId) {
        return new DrawingSessionContext(sessionId, roomId, playerId);
    }

    private DrawStartMessage drawStart(int round) {
        return new DrawStartMessage(DrawingProtocol.VERSION, round, UUID.randomUUID(), 0.1, 0.2, 255, 0, 0, 3);
    }

    private ClearCanvasMessage clearCanvas(int round) {
        return new ClearCanvasMessage(DrawingProtocol.VERSION, round);
    }

    // ── GW-05: stale round batch rejected after round transition ──────────────

    @Test
    void staleRoundBatch_rejectedAfterRoundTransition() {
        // Round 2, drawer = player-A
        cache.update("room-1", DrawingRoomState.playing("player-A", 2));

        DrawingSessionContext sessionA = session("ws-A", "room-1", "player-A");

        // player-A sends DRAW with round=2 → ACCEPTED
        DrawingAuthorizationService.AuthResult result1 = authService.authorize(sessionA, drawStart(2));
        assertThat(result1.isAuthorized()).isTrue();

        // Round transitions to 3 with new drawer player-B
        cache.update("room-1", DrawingRoomState.playing("player-B", 3));

        DrawingSessionContext sessionB = session("ws-B", "room-1", "player-B");

        // player-B is the current drawer, but sends a stale DRAW with old round=2 → REJECTED (WRONG_ROUND)
        DrawingAuthorizationService.AuthResult result2 = authService.authorize(sessionB, drawStart(2));
        assertThat(result2.isAuthorized()).isFalse();
        DrawingAuthorizationService.AuthResult.Rejected rejected2 =
                (DrawingAuthorizationService.AuthResult.Rejected) result2;
        assertThat(rejected2.reason()).isEqualTo("WRONG_ROUND");

        // player-A (old drawer) sending old round=2 is also REJECTED (NOT_DRAWER)
        DrawingAuthorizationService.AuthResult resultOldDrawer = authService.authorize(sessionA, drawStart(2));
        assertThat(resultOldDrawer.isAuthorized()).isFalse();
        assertThat(((DrawingAuthorizationService.AuthResult.Rejected) resultOldDrawer).reason()).isEqualTo("NOT_DRAWER");
    }

    // ── GW-06: old drawer rejected, new drawer accepted after round change ────

    @Test
    void oldDrawer_rejectedAfterRoundChange_newDrawer_accepted() {
        // Round 2: drawer = player-A
        cache.update("room-1", DrawingRoomState.playing("player-A", 2));

        DrawingSessionContext sessionA = session("ws-A", "room-1", "player-A");
        DrawingSessionContext sessionB = session("ws-B", "room-1", "player-B");

        // player-A is drawer — ACCEPTED for round 2
        assertThat(authService.authorize(sessionA, drawStart(2)).isAuthorized()).isTrue();

        // Round transitions: player-B becomes drawer for round 3
        cache.update("room-1", DrawingRoomState.playing("player-B", 3));

        // player-A continues sending — REJECTED (both NOT_DRAWER and WRONG_ROUND apply; NOT_DRAWER fires first)
        DrawingAuthorizationService.AuthResult resultA = authService.authorize(sessionA, drawStart(3));
        assertThat(resultA.isAuthorized()).isFalse();
        // player-B is now the drawer — ACCEPTED for round 3
        DrawingAuthorizationService.AuthResult resultB = authService.authorize(sessionB, drawStart(3));
        assertThat(resultB.isAuthorized()).isTrue();
    }

    @Test
    void clearCanvas_rejectedForOldDrawer_afterRoundChange() {
        cache.update("room-1", DrawingRoomState.playing("player-A", 2));

        // Transition to round 3
        cache.update("room-1", DrawingRoomState.playing("player-B", 3));

        DrawingSessionContext sessionA = session("ws-A", "room-1", "player-A");

        // player-A sends CLEAR_CANVAS for old round 2 — REJECTED
        DrawingAuthorizationService.AuthResult result = authService.authorize(sessionA, clearCanvas(2));
        assertThat(result.isAuthorized()).isFalse();
    }

    // ── GW-13: game finished → all drawing mutations rejected ─────────────────

    @Test
    void afterGameFinished_cacheEvicted_drawingRejected() {
        cache.update("room-1", DrawingRoomState.playing("player-A", 2));

        // Game ends: evict cache
        cache.remove("room-1");

        DrawingSessionContext sessionA = session("ws-A", "room-1", "player-A");

        DrawingAuthorizationService.AuthResult result = authService.authorize(sessionA, drawStart(2));
        assertThat(result.isAuthorized()).isFalse();
        DrawingAuthorizationService.AuthResult.Rejected rejected =
                (DrawingAuthorizationService.AuthResult.Rejected) result;
        assertThat(rejected.reason()).isEqualTo("GAME_NOT_ACTIVE");
    }

    @Test
    void afterGameFinished_clearCanvas_rejected() {
        cache.update("room-1", DrawingRoomState.playing("player-A", 3));
        cache.remove("room-1");

        DrawingSessionContext sessionA = session("ws-A", "room-1", "player-A");
        DrawingAuthorizationService.AuthResult result = authService.authorize(sessionA, clearCanvas(3));
        assertThat(result.isAuthorized()).isFalse();
    }

    // ── Cache size invariant ──────────────────────────────────────────────────

    @Test
    void cacheIsIsolatedPerRoom() {
        cache.update("room-1", DrawingRoomState.playing("player-A", 1));
        cache.update("room-2", DrawingRoomState.playing("player-B", 1));

        // Evict only room-1
        cache.remove("room-1");

        assertThat(cache.get("room-1")).isEmpty();
        assertThat(cache.get("room-2")).isPresent();
    }
}
