package com.drawgame.realtime_gateway.drawing.routing;

import com.drawgame.realtime_gateway.drawing.protocol.*;
import com.drawgame.realtime_gateway.drawing.redis.DrawingRedisPublisher;
import com.drawgame.realtime_gateway.drawing.transport.DrawingSessionContext;
import com.drawgame.realtime_gateway.drawing.transport.DrawingWebSocketEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrawingMessageRouterTest {

    @Mock
    private DrawingAuthorizationService authService;

    @Mock
    private DrawingBroadcaster broadcaster;

    @Mock
    private DrawingRedisPublisher redisPublisher;

    @Mock
    private DrawingWebSocketEncoder encoder;

    private DrawingMessageRouter router;

    private static final byte[] FAKE_BYTES = new byte[]{0x01, 0x02, 0x03};

    @BeforeEach
    void setUp() {
        router = new DrawingMessageRouter(authService, broadcaster, redisPublisher, encoder);
    }

    private DrawStartMessage drawStart(int round) {
        return new DrawStartMessage(
                DrawingProtocol.VERSION, round, UUID.randomUUID(),
                0.5, 0.5, 255, 0, 0, 4
        );
    }

    private DrawingSessionContext session(String sessionId, String roomId, String playerId) {
        return new DrawingSessionContext(sessionId, roomId, playerId);
    }

    @Test
    void validDrawing_broadcasts_and_publishes() {
        DrawingSessionContext ctx = session("ws-01", "room-ABC", "player-01");
        DrawStartMessage msg = drawStart(2);

        when(authService.authorize(ctx, msg)).thenReturn(DrawingAuthorizationService.AuthResult.ok());
        when(encoder.encodeToBytes(msg)).thenReturn(FAKE_BYTES);
        when(redisPublisher.publish(eq("room-ABC"), eq(FAKE_BYTES))).thenReturn(Mono.empty());

        StepVerifier.create(router.handle(ctx, msg))
                .verifyComplete();

        verify(broadcaster).broadcastBytesToRoomExcept("room-ABC", "ws-01", FAKE_BYTES);
        verify(redisPublisher).publish("room-ABC", FAKE_BYTES);
        assertThat(router.getAcceptedCount()).isEqualTo(1);
        assertThat(router.getRejectedCount()).isEqualTo(0);
    }

    @Test
    void rejectedDrawing_doesNotBroadcast_doesNotPublish() {
        DrawingSessionContext ctx = session("ws-02", "room-ABC", "player-02");
        DrawStartMessage msg = drawStart(2);

        when(authService.authorize(ctx, msg))
                .thenReturn(DrawingAuthorizationService.AuthResult.reject("NOT_DRAWER"));

        StepVerifier.create(router.handle(ctx, msg))
                .verifyComplete();

        verifyNoInteractions(broadcaster);
        verifyNoInteractions(redisPublisher);
        verifyNoInteractions(encoder);
        assertThat(router.getRejectedCount()).isEqualTo(1);
        assertThat(router.getAcceptedCount()).isEqualTo(0);
    }

    @Test
    void redisFailure_doesNotPropagateError() {
        DrawingSessionContext ctx = session("ws-01", "room-ABC", "player-01");
        DrawStartMessage msg = drawStart(2);

        when(authService.authorize(ctx, msg)).thenReturn(DrawingAuthorizationService.AuthResult.ok());
        when(encoder.encodeToBytes(msg)).thenReturn(FAKE_BYTES);
        // Publisher returns empty (already handles errors internally)
        when(redisPublisher.publish(any(), any())).thenReturn(Mono.empty());

        // Should still complete without error even if Redis "fails"
        StepVerifier.create(router.handle(ctx, msg))
                .verifyComplete();

        // Local broadcast still happened
        verify(broadcaster).broadcastBytesToRoomExcept("room-ABC", "ws-01", FAKE_BYTES);
    }

    @Test
    void encodeCalledOnce_reusedForBothBroadcastAndPublish() {
        DrawingSessionContext ctx = session("ws-01", "room-ABC", "player-01");
        DrawStartMessage msg = drawStart(1);

        when(authService.authorize(ctx, msg)).thenReturn(DrawingAuthorizationService.AuthResult.ok());
        when(encoder.encodeToBytes(msg)).thenReturn(FAKE_BYTES);
        when(redisPublisher.publish(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(router.handle(ctx, msg)).verifyComplete();

        // encodeToBytes must be called exactly once regardless of downstream usage
        verify(encoder, times(1)).encodeToBytes(msg);
    }

    @Test
    void clearCanvasMessage_isBroadcastAndPublished() {
        DrawingSessionContext ctx = session("ws-01", "room-ABC", "player-01");
        ClearCanvasMessage msg = new ClearCanvasMessage(DrawingProtocol.VERSION, 2);

        when(authService.authorize(ctx, msg)).thenReturn(DrawingAuthorizationService.AuthResult.ok());
        when(encoder.encodeToBytes(msg)).thenReturn(FAKE_BYTES);
        when(redisPublisher.publish(eq("room-ABC"), eq(FAKE_BYTES))).thenReturn(Mono.empty());

        StepVerifier.create(router.handle(ctx, msg)).verifyComplete();

        verify(broadcaster).broadcastBytesToRoomExcept("room-ABC", "ws-01", FAKE_BYTES);
        verify(redisPublisher).publish("room-ABC", FAKE_BYTES);
    }

    @Test
    void drawEndMessage_isBroadcastAndPublished() {
        DrawingSessionContext ctx = session("ws-01", "room-ABC", "player-01");
        DrawEndMessage msg = new DrawEndMessage(DrawingProtocol.VERSION, 2, UUID.randomUUID());

        when(authService.authorize(ctx, msg)).thenReturn(DrawingAuthorizationService.AuthResult.ok());
        when(encoder.encodeToBytes(msg)).thenReturn(FAKE_BYTES);
        when(redisPublisher.publish(eq("room-ABC"), eq(FAKE_BYTES))).thenReturn(Mono.empty());

        StepVerifier.create(router.handle(ctx, msg)).verifyComplete();

        verify(broadcaster).broadcastBytesToRoomExcept("room-ABC", "ws-01", FAKE_BYTES);
        verify(redisPublisher).publish("room-ABC", FAKE_BYTES);
    }
}

