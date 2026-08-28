package com.drawgame.realtime_gateway.websocket;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.connection.OutboundFrame;
import com.drawgame.realtime_gateway.drawing.transport.DrawingWebSocketTransport;
import com.drawgame.realtime_gateway.websocket.handler.GameCommandHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class GameWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ConnectionManager connectionManager;
    private final GameCommandHandler commandHandler;
    private final DrawingWebSocketTransport drawingTransport;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GameWebSocketHandler(
            ConnectionManager connectionManager,
            GameCommandHandler commandHandler,
            DrawingWebSocketTransport drawingTransport
    ) {
        this.connectionManager = connectionManager;
        this.commandHandler = commandHandler;
        this.drawingTransport = drawingTransport;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        Flux<OutboundFrame> outboundMessages = connectionManager.register(sessionId);

        Mono<Void> inbound = session.receive()
                .flatMap(message -> {
                    WebSocketMessage.Type type = message.getType();
                    if (type == WebSocketMessage.Type.TEXT) {
                        String rawMessage = message.getPayloadAsText();
                        log.info("Received TEXT from session {}: {}", sessionId, rawMessage);
                        try {
                            JsonNode jsonNode = objectMapper.readTree(rawMessage);
                            return commandHandler.handleCommand(sessionId, jsonNode)
                                    .flatMap(responseMsg -> {
                                        if (responseMsg != null && !responseMsg.isEmpty()) {
                                            return session.send(Mono.just(session.textMessage(responseMsg)));
                                        }
                                        return Mono.empty();
                                    });
                        } catch (Exception e) {
                            log.error("Failed to parse JSON from session {}", sessionId, e);
                            String errorJson = "{\"type\":\"ERROR\",\"code\":\"INVALID_JSON\",\"message\":\"Invalid JSON payload\"}";
                            return session.send(Mono.just(session.textMessage(errorJson)));
                        }
                    } else if (type == WebSocketMessage.Type.BINARY) {
                        log.debug("Received BINARY frame from session {}", sessionId);
                        return drawingTransport.handleBinaryMessage(session, message);
                    } else {
                        log.trace("Received frame of type {} from session {}", type, sessionId);
                        return Mono.empty();
                    }
                })
                .doOnError(error -> log.error("WebSocket error on session {}", sessionId, error))
                .doFinally(signalType -> connectionManager.remove(sessionId))
                .then();

        Mono<Void> outbound = session.send(
                outboundMessages.map(frame -> switch (frame) {
                    case OutboundFrame.TextFrame textFrame -> session.textMessage(textFrame.text());
                    case OutboundFrame.BinaryFrame binaryFrame -> session.binaryMessage(factory -> factory.wrap(binaryFrame.bytes()));
                })
        );

        return Mono.when(inbound, outbound);
    }
}