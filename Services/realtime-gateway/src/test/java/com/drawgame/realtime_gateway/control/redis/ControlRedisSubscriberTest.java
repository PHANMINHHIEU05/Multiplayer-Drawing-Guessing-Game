package com.drawgame.realtime_gateway.control.redis;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.control.LocalRoomBroadcaster;
import com.drawgame.realtime_gateway.drawing.recovery.DrawingRecoveryRepository;
import com.drawgame.realtime_gateway.drawing.routing.DrawingRoomState;
import com.drawgame.realtime_gateway.drawing.routing.DrawingRoomStateCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TV6 — unit tests for cross-Gateway CONTROL event fanout:
 * envelope round-trip, self-echo prevention, remote room routing, event type
 * preservation, and drawing-cache refresh on round lifecycle events.
 */
@ExtendWith(MockitoExtension.class)
class ControlRedisSubscriberTest {

    @Mock private ReactiveRedisMessageListenerContainer listenerContainer;
    @Mock private ConnectionManager connectionManager;
    @Mock private DrawingRoomStateCache drawingRoomStateCache;
    @Mock private DrawingRecoveryRepository recoveryRepository;

    private LocalRoomBroadcaster localBroadcaster;
    private ControlRedisSubscriber subscriber;

    private final Flux<ReactiveSubscription.PatternMessage<String, String, String>> messages =
            Flux.never();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(recoveryRepository.resetForNewRound(any())).thenReturn(Mono.empty());
        lenient().when(recoveryRepository.removeAll(any())).thenReturn(Mono.empty());
        localBroadcaster = new LocalRoomBroadcaster(connectionManager);
        when(listenerContainer.receive(any(PatternTopic.class))).thenReturn((Flux) messages);
        subscriber = new ControlRedisSubscriber(
                listenerContainer, connectionManager, localBroadcaster, drawingRoomStateCache,
                recoveryRepository, "gateway-1");
        subscriber.subscribe();
    }

    private void deliver(ControlEventEnvelope envelope) {
        subscriber.handleMessageForTest(ControlEventCodec.write(envelope));
    }

    private void deliverRaw(String json) {
        subscriber.handleMessageForTest(json);
    }

    @Test
    @DisplayName("Envelope codec round-trip preserves all fields")
    void envelopeRoundTrip() {
        ControlEventEnvelope envelope = new ControlEventEnvelope(
                "gateway-1", "ROOM01", "PLAYER_JOINED", "evt-42", "{\"type\":\"PLAYER_JOINED\"}");
        ControlEventEnvelope parsed = ControlEventCodec.read(ControlEventCodec.write(envelope));
        assertThat(parsed).isNotNull();
        assertThat(parsed.originGatewayId()).isEqualTo("gateway-1");
        assertThat(parsed.targetRoomId()).isEqualTo("ROOM01");
        assertThat(parsed.eventType()).isEqualTo("PLAYER_JOINED");
        assertThat(parsed.eventId()).isEqualTo("evt-42");
        assertThat(parsed.payload()).isEqualTo("{\"type\":\"PLAYER_JOINED\"}");
    }

    @Test
    @DisplayName("Malformed envelope JSON is dropped without throwing")
    void malformedJsonDropped() {
        deliverRaw("this is not json");
        // No broadcast happened, no exception
        verify(connectionManager, never()).broadcastToRoom(any(), any());
    }

    @Test
    @DisplayName("Self-echo: events originating from THIS gateway are ignored")
    void selfEchoIgnored() {
        deliver(new ControlEventEnvelope(
                "gateway-1", "ROOM01", "PLAYER_JOINED", "evt-1", "{\"type\":\"PLAYER_JOINED\"}"));
        verify(connectionManager, never()).broadcastToRoom(any(), any());
        assertThat(subscriber.getSelfEchoIgnoredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Remote event: fanned out locally to the target room only, payload preserved")
    void remoteEventFanout() {
        String payload = "{\"type\":\"CHAT_MESSAGE\",\"payload\":{\"content\":\"hello\"}}";
        deliver(new ControlEventEnvelope("gateway-2", "ROOM01", "CHAT_MESSAGE", "evt-2", payload));
        verify(connectionManager).broadcastToRoom("ROOM01", payload);
        assertThat(subscriber.getBroadcastCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Ephemeral reactions use the existing cross-Gateway room fanout")
    void reactionFanout() {
        String payload = "{\"type\":\"REACTION\",\"roomId\":\"ROOM01\",\"reactionType\":\"🔥\"}";
        deliver(new ControlEventEnvelope("gateway-2", "ROOM01", "REACTION", "evt-reaction", payload));
        verify(connectionManager).broadcastToRoom("ROOM01", payload);
        verify(drawingRoomStateCache, never()).update(any(), any());
        verify(drawingRoomStateCache, never()).remove(any());
    }

    @Test
    @DisplayName("Room isolation: event for ROOM02 never touches ROOM01 sessions")
    void roomIsolation() {
        String payload = "{\"type\":\"PLAYER_JOINED\"}";
        deliver(new ControlEventEnvelope("gateway-2", "ROOM02", "PLAYER_JOINED", "evt-3", payload));
        verify(connectionManager).broadcastToRoom("ROOM02", payload);
        verify(connectionManager, never()).broadcastToRoom(eq("ROOM01"), any());
    }

    @Test
    @DisplayName("Game Service origin is NOT self-echo-suppressed — every gateway fans out")
    void gameServiceOriginFanout() {
        String payload = "{\"type\":\"ROUND_STARTED\",\"currentRound\":2,\"drawerId\":\"p2\"}";
        deliver(new ControlEventEnvelope("game-service", "ROOM01", "ROUND_STARTED", "evt-4", payload));
        verify(connectionManager).broadcastToRoom("ROOM01", payload);
    }

    @Test
    @DisplayName("ROUND_STARTED from Game Service refreshes drawing authorization cache")
    void roundStartedRefreshesCache() {
        String payload = "{\"type\":\"ROUND_STARTED\",\"roomId\":\"ROOM01\",\"currentRound\":2,\"drawerId\":\"p2\",\"status\":\"PLAYING\"}";
        deliver(new ControlEventEnvelope("game-service", "ROOM01", "ROUND_STARTED", "evt-5", payload));

        ArgumentCaptor<DrawingRoomState> captor = ArgumentCaptor.forClass(DrawingRoomState.class);
        verify(drawingRoomStateCache).update(eq("ROOM01"), captor.capture());
        assertThat(captor.getValue().currentRound()).isEqualTo(2);
        assertThat(captor.getValue().currentDrawerId()).isEqualTo("p2");
    }

    @Test
    @DisplayName("GAME_FINISHED from Game Service evicts drawing cache")
    void gameFinishedEvictsCache() {
        String payload = "{\"type\":\"GAME_FINISHED\",\"roomId\":\"ROOM01\",\"status\":\"FINISHED\"}";
        deliver(new ControlEventEnvelope("game-service", "ROOM01", "GAME_FINISHED", "evt-6", payload));
        verify(drawingRoomStateCache).remove("ROOM01");
        verify(connectionManager).broadcastToRoom("ROOM01", payload);
    }

    @Test
    @DisplayName("Gateway-originated events (e.g. PLAYER_GUESSED_CORRECTLY) do not touch drawing cache")
    void gatewayOriginDoesNotTouchCache() {
        String payload = "{\"type\":\"PLAYER_GUESSED_CORRECTLY\",\"playerId\":\"p1\"}";
        deliver(new ControlEventEnvelope("gateway-2", "ROOM01", "PLAYER_GUESSED_CORRECTLY", "evt-7", payload));
        verify(drawingRoomStateCache, never()).update(any(), any());
        verify(drawingRoomStateCache, never()).remove(any());
        verify(connectionManager).broadcastToRoom("ROOM01", payload);
    }
}
