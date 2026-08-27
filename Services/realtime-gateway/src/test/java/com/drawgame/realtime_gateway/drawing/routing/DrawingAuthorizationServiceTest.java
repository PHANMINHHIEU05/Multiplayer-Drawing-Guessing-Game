package com.drawgame.realtime_gateway.drawing.routing;

import com.drawgame.realtime_gateway.drawing.protocol.*;
import com.drawgame.realtime_gateway.drawing.transport.DrawingSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DrawingAuthorizationServiceTest {

    private DrawingRoomStateCache cache;
    private DrawingAuthorizationService service;

    @BeforeEach
    void setUp() {
        cache = new DrawingRoomStateCache();
        service = new DrawingAuthorizationService(cache);

        // Seed a playing game: room-ABC, round 2, drawer = player-01
        cache.update("room-ABC", DrawingRoomState.playing("player-01", 2));
    }

    /** Helper — creates a minimal DrawStartMessage for the given round. */
    private DrawStartMessage drawStart(int round) {
        return new DrawStartMessage(
                DrawingProtocol.VERSION,
                round,
                UUID.randomUUID(),
                0.5, 0.5,   // normalized coordinates
                255, 0, 0,  // red color
                4           // brush width
        );
    }

    private DrawingSessionContext session(String sessionId, String roomId, String playerId) {
        return new DrawingSessionContext(sessionId, roomId, playerId);
    }

    // --- Happy path ---

    @Test
    void validDrawer_correctRound_isAuthorized() {
        DrawingSessionContext ctx = session("ws-01", "room-ABC", "player-01");
        DrawingAuthorizationService.AuthResult result = service.authorize(ctx, drawStart(2));
        assertThat(result.isAuthorized()).isTrue();
    }

    // --- Rejection cases ---

    @Test
    void noRoom_isRejected() {
        DrawingSessionContext ctx = session("ws-01", null, "player-01");
        DrawingAuthorizationService.AuthResult result = service.authorize(ctx, drawStart(2));
        assertThat(result.isAuthorized()).isFalse();
        assertThat(((DrawingAuthorizationService.AuthResult.Rejected) result).reason())
                .isEqualTo("NO_ROOM");
    }

    @Test
    void blankRoom_isRejected() {
        DrawingSessionContext ctx = session("ws-01", "  ", "player-01");
        DrawingAuthorizationService.AuthResult result = service.authorize(ctx, drawStart(2));
        assertThat(result.isAuthorized()).isFalse();
        assertThat(((DrawingAuthorizationService.AuthResult.Rejected) result).reason())
                .isEqualTo("NO_ROOM");
    }

    @Test
    void noPlayer_isRejected() {
        DrawingSessionContext ctx = session("ws-01", "room-ABC", null);
        DrawingAuthorizationService.AuthResult result = service.authorize(ctx, drawStart(2));
        assertThat(result.isAuthorized()).isFalse();
        assertThat(((DrawingAuthorizationService.AuthResult.Rejected) result).reason())
                .isEqualTo("NO_PLAYER");
    }

    @Test
    void noGameState_isRejected() {
        DrawingSessionContext ctx = session("ws-01", "room-UNKNOWN", "player-01");
        DrawingAuthorizationService.AuthResult result = service.authorize(ctx, drawStart(2));
        assertThat(result.isAuthorized()).isFalse();
        assertThat(((DrawingAuthorizationService.AuthResult.Rejected) result).reason())
                .isEqualTo("GAME_NOT_ACTIVE");
    }

    @Test
    void gameNotPlaying_isRejected() {
        cache.update("room-WAITING", new DrawingRoomState("player-01", 1, "WAITING"));
        DrawingSessionContext ctx = session("ws-01", "room-WAITING", "player-01");
        DrawingAuthorizationService.AuthResult result = service.authorize(ctx, drawStart(1));
        assertThat(result.isAuthorized()).isFalse();
        assertThat(((DrawingAuthorizationService.AuthResult.Rejected) result).reason())
                .isEqualTo("GAME_NOT_PLAYING");
    }

    @Test
    void nonDrawer_isRejected() {
        // player-02 is a guesser, only player-01 is the drawer
        DrawingSessionContext ctx = session("ws-02", "room-ABC", "player-02");
        DrawingAuthorizationService.AuthResult result = service.authorize(ctx, drawStart(2));
        assertThat(result.isAuthorized()).isFalse();
        assertThat(((DrawingAuthorizationService.AuthResult.Rejected) result).reason())
                .isEqualTo("NOT_DRAWER");
    }

    @Test
    void wrongRound_isRejected() {
        // Current round is 2, message says round 1 (stale)
        DrawingSessionContext ctx = session("ws-01", "room-ABC", "player-01");
        DrawingAuthorizationService.AuthResult result = service.authorize(ctx, drawStart(1));
        assertThat(result.isAuthorized()).isFalse();
        assertThat(((DrawingAuthorizationService.AuthResult.Rejected) result).reason())
                .isEqualTo("WRONG_ROUND");
    }

    @Test
    void clearCanvas_validDrawer_isAuthorized() {
        DrawingSessionContext ctx = session("ws-01", "room-ABC", "player-01");
        ClearCanvasMessage msg = new ClearCanvasMessage(DrawingProtocol.VERSION, 2);
        DrawingAuthorizationService.AuthResult result = service.authorize(ctx, msg);
        assertThat(result.isAuthorized()).isTrue();
    }

    @Test
    void drawEnd_validDrawer_isAuthorized() {
        DrawingSessionContext ctx = session("ws-01", "room-ABC", "player-01");
        DrawEndMessage msg = new DrawEndMessage(DrawingProtocol.VERSION, 2, UUID.randomUUID());
        DrawingAuthorizationService.AuthResult result = service.authorize(ctx, msg);
        assertThat(result.isAuthorized()).isTrue();
    }
}


