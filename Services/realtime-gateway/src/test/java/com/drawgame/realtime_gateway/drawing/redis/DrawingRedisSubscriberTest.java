package com.drawgame.realtime_gateway.drawing.redis;

import com.drawgame.realtime_gateway.drawing.routing.DrawingBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TV3 Stabilization — Tests for cross-Gateway Redis Pub/Sub:
 *  GW-02: Message from remote Gateway is broadcast locally to all connections in room
 *  GW-12: Self-echo prevention (messages originating from this gateway instance are ignored)
 */
@ExtendWith(MockitoExtension.class)
class DrawingRedisSubscriberTest {

    @Mock
    private ReactiveRedisMessageListenerContainer listenerContainer;

    @Mock
    private DrawingBroadcaster broadcaster;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String LOCAL_GATEWAY_ID = "gateway-local-1";

    private DrawingRedisSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new DrawingRedisSubscriber(listenerContainer, broadcaster, objectMapper, LOCAL_GATEWAY_ID);
    }

    @Test
    void remoteGatewayMessage_isBroadcastLocally_GW02() throws Exception {
        byte[] drawingBytes = new byte[]{0x01, 0x02, 0x03};
        RedisDrawingEnvelope envelope = new RedisDrawingEnvelope("gateway-remote-2", "room-100", drawingBytes);
        String json = objectMapper.writeValueAsString(envelope);

        ReactiveSubscription.PatternMessage<String, String, String> mockMessage = mock(ReactiveSubscription.PatternMessage.class);
        when(mockMessage.getMessage()).thenReturn(json);

        when(listenerContainer.receive(any(PatternTopic.class)))
                .thenReturn(Flux.just(mockMessage));

        subscriber.subscribe();

        // Must broadcast to local room with null sender (so all local clients receive it)
        verify(broadcaster, times(1)).broadcastBytesToRoomExcept(eq("room-100"), isNull(), eq(drawingBytes));
        assertThat(subscriber.getBroadcastCount()).isEqualTo(1);
        assertThat(subscriber.getSelfEchoIgnoredCount()).isEqualTo(0);
    }

    @Test
    void selfEchoMessage_isIgnored_GW12() throws Exception {
        byte[] drawingBytes = new byte[]{0x01, 0x02};
        // originGatewayId matches this subscriber's LOCAL_GATEWAY_ID
        RedisDrawingEnvelope envelope = new RedisDrawingEnvelope(LOCAL_GATEWAY_ID, "room-100", drawingBytes);
        String json = objectMapper.writeValueAsString(envelope);

        ReactiveSubscription.PatternMessage<String, String, String> mockMessage = mock(ReactiveSubscription.PatternMessage.class);
        when(mockMessage.getMessage()).thenReturn(json);

        when(listenerContainer.receive(any(PatternTopic.class)))
                .thenReturn(Flux.just(mockMessage));

        subscriber.subscribe();

        // Must NOT broadcast locally
        verify(broadcaster, never()).broadcastBytesToRoomExcept(any(), any(), any());
        assertThat(subscriber.getBroadcastCount()).isEqualTo(0);
        assertThat(subscriber.getSelfEchoIgnoredCount()).isEqualTo(1);
    }

    @Test
    void malformedMessage_isIgnoredWithoutCrashing() {
        ReactiveSubscription.PatternMessage<String, String, String> mockMessage = mock(ReactiveSubscription.PatternMessage.class);
        when(mockMessage.getMessage()).thenReturn("not valid json at all");

        when(listenerContainer.receive(any(PatternTopic.class)))
                .thenReturn(Flux.just(mockMessage));

        subscriber.subscribe();

        verify(broadcaster, never()).broadcastBytesToRoomExcept(any(), any(), any());
        assertThat(subscriber.getDeserializeErrorCount()).isEqualTo(1);
    }
}
