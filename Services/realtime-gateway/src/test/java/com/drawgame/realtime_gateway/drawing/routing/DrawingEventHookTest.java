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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TV3 Stabilization (TV3-G08) — Recovery groundwork hook is called correctly.
 */
@ExtendWith(MockitoExtension.class)
class DrawingEventHookTest {

    @Mock private DrawingAuthorizationService authService;
    @Mock private DrawingBroadcaster broadcaster;
    @Mock private DrawingRedisPublisher redisPublisher;
    @Mock private DrawingWebSocketEncoder encoder;
    @Mock private DrawingEventHook eventHook;

    private DrawingMessageRouter router;

    private static final byte[] FAKE_BYTES = {0x10, 0x20};

    @BeforeEach
    void setUp() {
        router = new DrawingMessageRouter(authService, broadcaster, redisPublisher, encoder, eventHook);
    }

    @Test
    void hook_onAccepted_calledAfterSuccessfulAuthorization() {
        DrawingSessionContext session = new DrawingSessionContext("ws-1", "room-1", "player-1");
        DrawStartMessage msg = new DrawStartMessage(DrawingProtocol.VERSION, 1, UUID.randomUUID(), 0.5, 0.5, 100, 150, 200, 3);

        when(encoder.encodeToBytes(any())).thenReturn(FAKE_BYTES);
        when(redisPublisher.publish(any(), any())).thenReturn(Mono.empty());
        when(authService.authorize(session, msg))
                .thenReturn(DrawingAuthorizationService.AuthResult.ok());

        router.handle(session, msg).block();

        verify(eventHook, times(1)).onAccepted(eq(session), eq(msg), eq(FAKE_BYTES));
    }

    @Test
    void hook_notCalled_whenAuthorizationFails() {
        DrawingSessionContext session = new DrawingSessionContext("ws-1", "room-1", "player-2");
        DrawStartMessage msg = new DrawStartMessage(DrawingProtocol.VERSION, 1, UUID.randomUUID(), 0.5, 0.5, 100, 150, 200, 3);

        when(authService.authorize(session, msg))
                .thenReturn(DrawingAuthorizationService.AuthResult.reject("NOT_DRAWER"));

        router.handle(session, msg).block();

        verify(eventHook, never()).onAccepted(any(), any(), any());
    }

    @Test
    void noOpHook_doesNotThrow() {
        NoOpDrawingEventHook noOp = new NoOpDrawingEventHook();
        DrawingSessionContext session = new DrawingSessionContext("ws-1", "room-1", "player-1");
        DrawStartMessage msg = new DrawStartMessage(DrawingProtocol.VERSION, 1, UUID.randomUUID(), 0.1, 0.2, 0, 0, 0, 1);

        // Must not throw
        noOp.onAccepted(session, msg, new byte[]{0x01});
        noOp.onRoundReset("room-1", 2);
        noOp.onGameFinished("room-1");
    }
}
