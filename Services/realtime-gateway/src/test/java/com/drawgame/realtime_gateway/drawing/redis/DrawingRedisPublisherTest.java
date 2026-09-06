package com.drawgame.realtime_gateway.drawing.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrawingRedisPublisherTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String GATEWAY_ID = "gateway-1";

    private DrawingRedisPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DrawingRedisPublisher(redisTemplate, objectMapper, GATEWAY_ID);
    }

    @Test
    void publish_sendsEnvelopeToCorrectChannel() throws Exception {
        byte[] payload = new byte[]{1, 2, 3, 4};
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(publisher.publish("room-xyz", payload))
                .verifyComplete();

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

        verify(redisTemplate).convertAndSend(channelCaptor.capture(), jsonCaptor.capture());

        assertThat(channelCaptor.getValue()).isEqualTo("drawing:room:room-xyz");

        RedisDrawingEnvelope envelope = objectMapper.readValue(jsonCaptor.getValue(), RedisDrawingEnvelope.class);
        assertThat(envelope.originGatewayId()).isEqualTo(GATEWAY_ID);
        assertThat(envelope.targetRoomId()).isEqualTo("room-xyz");
        assertThat(envelope.drawingBytes()).isEqualTo(payload);
    }

    @Test
    void publish_onError_isIsolatedAndDoesNotThrow() {
        byte[] payload = new byte[]{1, 2};
        when(redisTemplate.convertAndSend(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis connection refused")));

        // Must complete normally (error suppressed inside publisher)
        StepVerifier.create(publisher.publish("room-xyz", payload))
                .verifyComplete();
    }
}
