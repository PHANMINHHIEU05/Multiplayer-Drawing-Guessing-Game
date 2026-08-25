package com.drawgame.realtime_gateway.drawing.transport;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.drawing.protocol.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class FullTransportRoundTripTest {

    @Mock
    private DrawingMessageHandler messageHandler;

    @Mock
    private ConnectionManager connectionManager;

    @Mock
    private WebSocketSession session;

    private BinaryDrawingDecoder decoder;
    private BinaryDrawingEncoder binaryEncoder;
    private DrawingWebSocketEncoder transportEncoder;
    private DrawingWebSocketTransport transport;
    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @BeforeEach
    void setUp() {
        decoder = new BinaryDrawingDecoder();
        binaryEncoder = new BinaryDrawingEncoder();
        transportEncoder = new DrawingWebSocketEncoder(binaryEncoder);
        transport = new DrawingWebSocketTransport(decoder, messageHandler, connectionManager);

        org.mockito.Mockito.lenient().when(session.getId()).thenReturn("session-roundtrip");
        org.mockito.Mockito.lenient().when(connectionManager.getRoomId("session-roundtrip")).thenReturn("room-roundtrip");
        org.mockito.Mockito.lenient().when(connectionManager.getPlayerId("session-roundtrip")).thenReturn("player-roundtrip");

        org.mockito.Mockito.lenient().when(session.binaryMessage(any())).thenAnswer(invocation -> {
            java.util.function.Function<org.springframework.core.io.buffer.DataBufferFactory, DataBuffer> fn = invocation.getArgument(0);
            DataBuffer db = fn.apply(bufferFactory);
            return new WebSocketMessage(WebSocketMessage.Type.BINARY, db);
        });
    }

    @Test
    void fullTransportRoundTrip_DrawBatch() {
        // 1. Prepare original message
        UUID strokeId = UUID.randomUUID();
        List<DrawingPoint> points = List.of(
                new DrawingPoint(0.1111f, 0.2222f),
                new DrawingPoint(0.3333f, 0.4444f),
                new DrawingPoint(0.5555f, 0.6666f)
        );
        DrawBatchMessage originalBatch = new DrawBatchMessage(1, 3, strokeId, 500L, points);

        // 2. Encode to binary payload
        byte[] inputBytes = transportEncoder.encodeToBytes(originalBatch);
        DataBuffer inputBuffer = bufferFactory.wrap(inputBytes);
        WebSocketMessage inboundWsMessage = new WebSocketMessage(WebSocketMessage.Type.BINARY, inputBuffer);

        // 3. Setup mock handler capture
        org.mockito.Mockito.when(messageHandler.handle(any(), any())).thenReturn(Mono.empty());

        // 4. Run through transport
        StepVerifier.create(transport.handleBinaryMessage(session, inboundWsMessage)).verifyComplete();

        // 5. Verify handler received message
        ArgumentCaptor<DrawingSessionContext> contextCaptor = ArgumentCaptor.forClass(DrawingSessionContext.class);
        ArgumentCaptor<DrawingMessage> messageCaptor = ArgumentCaptor.forClass(DrawingMessage.class);

        org.mockito.Mockito.verify(messageHandler).handle(contextCaptor.capture(), messageCaptor.capture());

        assertEquals("session-roundtrip", contextCaptor.getValue().sessionId());
        assertEquals("room-roundtrip", contextCaptor.getValue().roomId());
        assertEquals("player-roundtrip", contextCaptor.getValue().playerId());

        DrawingMessage capturedMsg = messageCaptor.getValue();
        assertTrue(capturedMsg instanceof DrawBatchMessage);

        // 6. Encode captured message outbound
        WebSocketMessage outboundWsMessage = transportEncoder.encode(session, capturedMsg);
        assertEquals(WebSocketMessage.Type.BINARY, outboundWsMessage.getType());

        DataBuffer outboundBuffer = outboundWsMessage.getPayload();
        byte[] outboundBytes = new byte[outboundBuffer.readableByteCount()];
        outboundBuffer.read(outboundBytes);

        // 7. Decode outbound bytes with decoder T1 and verify equality
        DrawingMessage finalDecodedMsg = decoder.decode(outboundBytes);
        assertTrue(finalDecodedMsg instanceof DrawBatchMessage);

        DrawBatchMessage finalBatch = (DrawBatchMessage) finalDecodedMsg;
        assertEquals(3, finalBatch.round());
        assertEquals(500L, finalBatch.seqStart());
        assertEquals(strokeId, finalBatch.strokeId());
        assertEquals(3, finalBatch.points().size());

        for (int i = 0; i < points.size(); i++) {
            assertEquals(points.get(i).x(), finalBatch.points().get(i).x(), 0.001f);
            assertEquals(points.get(i).y(), finalBatch.points().get(i).y(), 0.001f);
        }
    }

    @Test
    void fullTransportRoundTrip_DrawStart() {
        UUID strokeId = UUID.randomUUID();
        DrawStartMessage originalStart = new DrawStartMessage(1, 1, strokeId, 0.88f, 0.12f, 200, 100, 50, 12);

        byte[] inputBytes = transportEncoder.encodeToBytes(originalStart);
        DataBuffer inputBuffer = bufferFactory.wrap(inputBytes);
        WebSocketMessage inboundWsMessage = new WebSocketMessage(WebSocketMessage.Type.BINARY, inputBuffer);

        org.mockito.Mockito.when(messageHandler.handle(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(transport.handleBinaryMessage(session, inboundWsMessage)).verifyComplete();

        ArgumentCaptor<DrawingMessage> messageCaptor = ArgumentCaptor.forClass(DrawingMessage.class);
        org.mockito.Mockito.verify(messageHandler).handle(any(), messageCaptor.capture());

        WebSocketMessage outboundWsMessage = transportEncoder.encode(session, messageCaptor.getValue());
        DataBuffer outboundBuffer = outboundWsMessage.getPayload();
        byte[] outboundBytes = new byte[outboundBuffer.readableByteCount()];
        outboundBuffer.read(outboundBytes);

        DrawingMessage finalDecodedMsg = decoder.decode(outboundBytes);
        assertTrue(finalDecodedMsg instanceof DrawStartMessage);

        DrawStartMessage finalStart = (DrawStartMessage) finalDecodedMsg;
        assertEquals(1, finalStart.round());
        assertEquals(strokeId, finalStart.strokeId());
        assertEquals(0.88f, finalStart.x(), 0.001f);
        assertEquals(0.12f, finalStart.y(), 0.001f);
        assertEquals(200, finalStart.red());
        assertEquals(100, finalStart.green());
        assertEquals(50, finalStart.blue());
        assertEquals(12, finalStart.width());
    }
}
