package com.drawgame.realtime_gateway.websocket;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.connection.OutboundFrame;
import com.drawgame.realtime_gateway.drawing.transport.DrawingWebSocketTransport;
import com.drawgame.realtime_gateway.websocket.handler.GameCommandHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameWebSocketHandlerDispatchTest {

    @Mock
    private ConnectionManager connectionManager;

    @Mock
    private GameCommandHandler commandHandler;

    @Mock
    private DrawingWebSocketTransport drawingTransport;

    @Mock
    private WebSocketSession session;

    private GameWebSocketHandler handler;
    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @BeforeEach
    void setUp() {
        handler = new GameWebSocketHandler(connectionManager, commandHandler, drawingTransport);

        lenient().when(session.getId()).thenReturn("session-abc");
        lenient().when(connectionManager.register("session-abc")).thenReturn(Flux.never());
        lenient().when(session.send(any())).thenReturn(Mono.empty());
        lenient().when(session.textMessage(anyString())).thenAnswer(inv -> {
            String txt = inv.getArgument(0);
            return new WebSocketMessage(WebSocketMessage.Type.TEXT, bufferFactory.wrap(txt.getBytes()));
        });
    }

    @Test
    void handle_TextMessage_DispatchedToCommandHandler_NotDrawingTransport() {
        String jsonStr = "{\"type\":\"GET_ROOM\",\"payload\":{\"roomId\":\"r-1\"}}";
        DataBuffer buffer = bufferFactory.wrap(jsonStr.getBytes());
        WebSocketMessage textMsg = new WebSocketMessage(WebSocketMessage.Type.TEXT, buffer);

        when(session.receive()).thenReturn(Flux.just(textMsg));
        when(commandHandler.handleCommand(eq("session-abc"), any())).thenReturn(Mono.just("{\"type\":\"ROOM_INFO\"}"));

        StepVerifier.create(handler.handle(session)).verifyComplete();

        verify(commandHandler).handleCommand(eq("session-abc"), any());
        verifyNoInteractions(drawingTransport);
    }

    @Test
    void handle_BinaryMessage_DispatchedToDrawingTransport_NotCommandHandler() {
        byte[] binaryData = new byte[]{0x01, 0x04, 0x00, 0x01}; // CLEAR_CANVAS bytes
        DataBuffer buffer = bufferFactory.wrap(binaryData);
        WebSocketMessage binaryMsg = new WebSocketMessage(WebSocketMessage.Type.BINARY, buffer);

        when(session.receive()).thenReturn(Flux.just(binaryMsg));
        when(drawingTransport.handleBinaryMessage(eq(session), eq(binaryMsg))).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(session)).verifyComplete();

        verify(drawingTransport).handleBinaryMessage(eq(session), eq(binaryMsg));
        verifyNoInteractions(commandHandler);
    }

    @Test
    void handle_PingMessage_IgnoredWithoutError() {
        DataBuffer buffer = bufferFactory.wrap(new byte[0]);
        WebSocketMessage pingMsg = new WebSocketMessage(WebSocketMessage.Type.PING, buffer);

        when(session.receive()).thenReturn(Flux.just(pingMsg));

        StepVerifier.create(handler.handle(session)).verifyComplete();

        verifyNoInteractions(commandHandler);
        verifyNoInteractions(drawingTransport);
    }
}
