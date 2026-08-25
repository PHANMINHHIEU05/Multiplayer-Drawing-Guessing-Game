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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrawingWebSocketTransportTest {

    @Mock
    private DrawingMessageHandler messageHandler;

    @Mock
    private ConnectionManager connectionManager;

    @Mock
    private WebSocketSession session;

    private BinaryDrawingDecoder decoder;
    private BinaryDrawingEncoder encoder;
    private DrawingWebSocketTransport transport;
    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @BeforeEach
    void setUp() {
        decoder = new BinaryDrawingDecoder();
        encoder = new BinaryDrawingEncoder();
        transport = new DrawingWebSocketTransport(decoder, messageHandler, connectionManager);

        lenient().when(session.getId()).thenReturn("session-123");
        lenient().when(connectionManager.getRoomId("session-123")).thenReturn("room-456");
        lenient().when(connectionManager.getPlayerId("session-123")).thenReturn("player-789");
    }

    @Test
    void handleBinaryMessage_DrawStart_DeliveredToHandlerWithSessionContext() {
        UUID strokeId = UUID.randomUUID();
        DrawStartMessage originalMsg = new DrawStartMessage(1, 3, strokeId, 0.25f, 0.75f, 255, 0, 128, 10);
        byte[] payloadBytes = encoder.encode(originalMsg);

        DataBuffer dataBuffer = bufferFactory.wrap(payloadBytes);
        WebSocketMessage wsMessage = new WebSocketMessage(WebSocketMessage.Type.BINARY, dataBuffer);

        when(messageHandler.handle(any(), any())).thenReturn(Mono.empty());

        Mono<Void> result = transport.handleBinaryMessage(session, wsMessage);

        StepVerifier.create(result).verifyComplete();

        ArgumentCaptor<DrawingSessionContext> contextCaptor = ArgumentCaptor.forClass(DrawingSessionContext.class);
        ArgumentCaptor<DrawingMessage> messageCaptor = ArgumentCaptor.forClass(DrawingMessage.class);

        verify(messageHandler).handle(contextCaptor.capture(), messageCaptor.capture());

        DrawingSessionContext capturedContext = contextCaptor.getValue();
        assertEquals("session-123", capturedContext.sessionId());
        assertEquals("room-456", capturedContext.roomId());
        assertEquals("player-789", capturedContext.playerId());

        assertTrue(messageCaptor.getValue() instanceof DrawStartMessage);
        DrawStartMessage receivedMsg = (DrawStartMessage) messageCaptor.getValue();
        assertEquals(3, receivedMsg.round());
        assertEquals(strokeId, receivedMsg.strokeId());
        assertEquals(255, receivedMsg.red());
        assertEquals(0, receivedMsg.green());
        assertEquals(128, receivedMsg.blue());
        assertEquals(10, receivedMsg.width());
        assertEquals(0.25f, receivedMsg.x(), 0.001f);
        assertEquals(0.75f, receivedMsg.y(), 0.001f);
    }

    @Test
    void handleBinaryMessage_DrawBatch_DeliveredToHandlerWithSessionContext() {
        UUID strokeId = UUID.randomUUID();
        List<DrawingPoint> points = List.of(
                new DrawingPoint(0.1f, 0.2f),
                new DrawingPoint(0.3f, 0.4f)
        );
        DrawBatchMessage originalMsg = new DrawBatchMessage(1, 2, strokeId, 105L, points);
        byte[] payloadBytes = encoder.encode(originalMsg);

        DataBuffer dataBuffer = bufferFactory.wrap(payloadBytes);
        WebSocketMessage wsMessage = new WebSocketMessage(WebSocketMessage.Type.BINARY, dataBuffer);

        when(messageHandler.handle(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(transport.handleBinaryMessage(session, wsMessage)).verifyComplete();

        ArgumentCaptor<DrawingMessage> messageCaptor = ArgumentCaptor.forClass(DrawingMessage.class);
        verify(messageHandler).handle(any(), messageCaptor.capture());

        assertTrue(messageCaptor.getValue() instanceof DrawBatchMessage);
        DrawBatchMessage receivedMsg = (DrawBatchMessage) messageCaptor.getValue();
        assertEquals(2, receivedMsg.round());
        assertEquals(105L, receivedMsg.seqStart());
        assertEquals(strokeId, receivedMsg.strokeId());
        assertEquals(2, receivedMsg.points().size());
        assertEquals(0.1f, receivedMsg.points().get(0).x(), 0.001f);
    }

    @Test
    void handleBinaryMessage_DrawEnd_DeliveredToHandlerWithSessionContext() {
        UUID strokeId = UUID.randomUUID();
        DrawEndMessage originalMsg = new DrawEndMessage((byte) 1, 5, strokeId);
        byte[] payloadBytes = encoder.encode(originalMsg);

        DataBuffer dataBuffer = bufferFactory.wrap(payloadBytes);
        WebSocketMessage wsMessage = new WebSocketMessage(WebSocketMessage.Type.BINARY, dataBuffer);

        when(messageHandler.handle(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(transport.handleBinaryMessage(session, wsMessage)).verifyComplete();

        ArgumentCaptor<DrawingMessage> messageCaptor = ArgumentCaptor.forClass(DrawingMessage.class);
        verify(messageHandler).handle(any(), messageCaptor.capture());

        assertTrue(messageCaptor.getValue() instanceof DrawEndMessage);
        DrawEndMessage receivedMsg = (DrawEndMessage) messageCaptor.getValue();
        assertEquals(5, receivedMsg.round());
        assertEquals(strokeId, receivedMsg.strokeId());
    }

    @Test
    void handleBinaryMessage_ClearCanvas_DeliveredToHandlerWithSessionContext() {
        ClearCanvasMessage originalMsg = new ClearCanvasMessage((byte) 1, 4);
        byte[] payloadBytes = encoder.encode(originalMsg);

        DataBuffer dataBuffer = bufferFactory.wrap(payloadBytes);
        WebSocketMessage wsMessage = new WebSocketMessage(WebSocketMessage.Type.BINARY, dataBuffer);

        when(messageHandler.handle(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(transport.handleBinaryMessage(session, wsMessage)).verifyComplete();

        ArgumentCaptor<DrawingMessage> messageCaptor = ArgumentCaptor.forClass(DrawingMessage.class);
        verify(messageHandler).handle(any(), messageCaptor.capture());

        assertTrue(messageCaptor.getValue() instanceof ClearCanvasMessage);
        ClearCanvasMessage receivedMsg = (ClearCanvasMessage) messageCaptor.getValue();
        assertEquals(4, receivedMsg.round());
    }

    @Test
    void handleBinaryMessage_UnsupportedVersion_RejectedAndLogged() {
        byte[] badPayload = new byte[]{0x02, 0x04, 0x00, 0x01}; // Version 2
        DataBuffer dataBuffer = bufferFactory.wrap(badPayload);
        WebSocketMessage wsMessage = new WebSocketMessage(WebSocketMessage.Type.BINARY, dataBuffer);

        StepVerifier.create(transport.handleBinaryMessage(session, wsMessage)).verifyComplete();

        verifyNoInteractions(messageHandler);
    }

    @Test
    void handleBinaryMessage_UnknownOpcode_RejectedAndLogged() {
        byte[] badPayload = new byte[]{0x01, 0x77, 0x00, 0x01}; // Opcode 0x77 unknown
        DataBuffer dataBuffer = bufferFactory.wrap(badPayload);
        WebSocketMessage wsMessage = new WebSocketMessage(WebSocketMessage.Type.BINARY, dataBuffer);

        StepVerifier.create(transport.handleBinaryMessage(session, wsMessage)).verifyComplete();

        verifyNoInteractions(messageHandler);
    }

    @Test
    void handleBinaryMessage_TruncatedFrame_RejectedAndLogged() {
        byte[] truncatedPayload = new byte[]{0x01, 0x01, 0x00}; // DRAW_START requires 29 bytes
        DataBuffer dataBuffer = bufferFactory.wrap(truncatedPayload);
        WebSocketMessage wsMessage = new WebSocketMessage(WebSocketMessage.Type.BINARY, dataBuffer);

        StepVerifier.create(transport.handleBinaryMessage(session, wsMessage)).verifyComplete();

        verifyNoInteractions(messageHandler);
    }

    @Test
    void handleBinaryMessage_HandlerError_ResumesSafely() {
        ClearCanvasMessage originalMsg = new ClearCanvasMessage((byte) 1, 1);
        byte[] payloadBytes = encoder.encode(originalMsg);

        DataBuffer dataBuffer = bufferFactory.wrap(payloadBytes);
        WebSocketMessage wsMessage = new WebSocketMessage(WebSocketMessage.Type.BINARY, dataBuffer);

        when(messageHandler.handle(any(), any())).thenReturn(Mono.error(new RuntimeException("Downstream handler exception")));

        StepVerifier.create(transport.handleBinaryMessage(session, wsMessage)).verifyComplete();

        verify(messageHandler).handle(any(), any());
    }
}
