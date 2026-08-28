package com.drawgame.realtime_gateway.websocket.handler;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.drawing.routing.DrawingRoomStateCache;
import com.drawgame.realtime_gateway.grpc.ChatGrpcClient;
import com.drawgame.realtime_gateway.grpc.GameGrpcClient;
import com.drawgame.realtime_gateway.grpc.RoomGrpcClient;
import com.drawgame.realtime_gateway.metrics.GatewayMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatPingPongTest {

    @Mock
    private GameGrpcClient gameGrpcClient;

    @Mock
    private RoomGrpcClient roomGrpcClient;

    @Mock
    private ChatGrpcClient chatGrpcClient;

    @Mock
    private DrawingRoomStateCache drawingRoomStateCache;

    private ConnectionManager connectionManager;
    private GatewayMetrics metrics;
    private GameCommandHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        metrics = new GatewayMetrics();
        connectionManager = new ConnectionManager(metrics, 256);
        handler = new GameCommandHandler(
                gameGrpcClient,
                roomGrpcClient,
                chatGrpcClient,
                connectionManager,
                drawingRoomStateCache,
                "test-gateway"
        );
    }

    @Test
    void testHandlePing_ReturnsPongWithTimestampsAndMetrics() throws Exception {
        String sessionId = "session-test";
        connectionManager.register(sessionId);

        long now = System.currentTimeMillis();
        String pingJson = "{\"type\":\"APP_PING\",\"payload\":{\"sentAt\":" + now + "},\"requestId\":\"req-ping-1\"}";
        JsonNode jsonNode = objectMapper.readTree(pingJson);

        StepVerifier.create(handler.handleCommand(sessionId, jsonNode))
                .assertNext(responseJson -> {
                    try {
                        JsonNode res = objectMapper.readTree(responseJson);
                        assertEquals("APP_PONG", res.get("type").asText());
                        assertEquals("req-ping-1", res.get("requestId").asText());
                        assertEquals(now, res.get("clientTimestamp").asLong());
                        assertTrue(res.has("serverTimestamp"));
                        assertEquals(0, res.get("queueSize").asInt());
                        assertEquals("test-gateway", res.get("gatewayId").asText());
                    } catch (Exception e) {
                        fail(e);
                    }
                })
                .verifyComplete();

        assertEquals(1, metrics.getHeartbeatPingReceived());
        assertEquals(1, metrics.getHeartbeatPongSent());
    }
}
