package com.drawgame.realtime_gateway.drawing.transport;

import com.drawgame.realtime_gateway.drawing.protocol.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrawingWebSocketEncoderTest {

    @Mock
    private WebSocketSession session;

    private BinaryDrawingDecoder decoder;
    private DrawingWebSocketEncoder encoder;
    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @BeforeEach
    void setUp() {
        decoder = new BinaryDrawingDecoder();
        encoder = new DrawingWebSocketEncoder();

        lenient().when(session.binaryMessage(any())).thenAnswer(invocation -> {
            java.util.function.Function<org.springframework.core.io.buffer.DataBufferFactory, DataBuffer> fn = invocation.getArgument(0);
            DataBuffer db = fn.apply(bufferFactory);
            return new WebSocketMessage(WebSocketMessage.Type.BINARY, db);
        });
    }

    @Test
    void encode_DrawStart_ReturnsBinaryWebSocketMessage() {
        UUID strokeId = UUID.randomUUID();
        DrawStartMessage originalMsg = new DrawStartMessage(1, 2, strokeId, 0.5f, 0.5f, 100, 150, 200, 5);

        WebSocketMessage wsMessage = encoder.encode(session, originalMsg);

        assertEquals(WebSocketMessage.Type.BINARY, wsMessage.getType());

        DataBuffer buffer = wsMessage.getPayload();
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);

        DrawingMessage decodedMsg = decoder.decode(bytes);
        assertTrue(decodedMsg instanceof DrawStartMessage);

        DrawStartMessage drawStart = (DrawStartMessage) decodedMsg;
        assertEquals(2, drawStart.round());
        assertEquals(strokeId, drawStart.strokeId());
        assertEquals(0.5f, drawStart.x(), 0.001f);
        assertEquals(0.5f, drawStart.y(), 0.001f);
        assertEquals(100, drawStart.red());
        assertEquals(150, drawStart.green());
        assertEquals(200, drawStart.blue());
        assertEquals(5, drawStart.width());
    }

    @Test
    void encode_DrawBatch_ReturnsBinaryWebSocketMessage() {
        UUID strokeId = UUID.randomUUID();
        List<DrawingPoint> points = List.of(
                new DrawingPoint(0.123f, 0.456f),
                new DrawingPoint(0.789f, 0.987f)
        );
        DrawBatchMessage originalMsg = new DrawBatchMessage(1, 1, strokeId, 200L, points);

        WebSocketMessage wsMessage = encoder.encode(session, originalMsg);

        assertEquals(WebSocketMessage.Type.BINARY, wsMessage.getType());

        DataBuffer buffer = wsMessage.getPayload();
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);

        DrawingMessage decodedMsg = decoder.decode(bytes);
        assertTrue(decodedMsg instanceof DrawBatchMessage);

        DrawBatchMessage batch = (DrawBatchMessage) decodedMsg;
        assertEquals(1, batch.round());
        assertEquals(200L, batch.seqStart());
        assertEquals(strokeId, batch.strokeId());
        assertEquals(2, batch.points().size());
    }

    @Test
    void encode_ClearCanvas_ReturnsBinaryWebSocketMessage() {
        ClearCanvasMessage originalMsg = new ClearCanvasMessage((byte) 1, 7);

        WebSocketMessage wsMessage = encoder.encode(session, originalMsg);

        assertEquals(WebSocketMessage.Type.BINARY, wsMessage.getType());

        DataBuffer buffer = wsMessage.getPayload();
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);

        DrawingMessage decodedMsg = decoder.decode(bytes);
        assertTrue(decodedMsg instanceof ClearCanvasMessage);

        ClearCanvasMessage clear = (ClearCanvasMessage) decodedMsg;
        assertEquals(7, clear.round());
    }

    @Test
    void encodeToBytes_MatchesEncoderOutput() {
        DrawEndMessage originalMsg = new DrawEndMessage((byte) 1, 3, UUID.randomUUID());

        byte[] bytes = encoder.encodeToBytes(originalMsg);
        assertNotNull(bytes);

        DrawingMessage decodedMsg = decoder.decode(bytes);
        assertTrue(decodedMsg instanceof DrawEndMessage);
    }
}
