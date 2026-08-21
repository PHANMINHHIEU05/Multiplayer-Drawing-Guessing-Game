package com.drawgame.realtime_gateway.websocket;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.websocket.handler.GameCommandHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GameWebSocketHandler implements WebSocketHandler {

    private final ConnectionManager connectionManager;
    private final GameCommandHandler commandHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GameWebSocketHandler(
            ConnectionManager connectionManager,
            GameCommandHandler commandHandler
    ) {
        this.connectionManager = connectionManager;
        this.commandHandler = commandHandler;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        Flux<String> outboundMessages = connectionManager.register(sessionId);

        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(rawMessage -> {
                    log.info("Received from session {}: {}", sessionId, rawMessage);
                    try {
                        JsonNode jsonNode = objectMapper.readTree(rawMessage);
                        return commandHandler.handleCommand(sessionId, jsonNode);
                    } catch (Exception e) {
                        log.error("Failed to parse JSON from session {}", sessionId, e);
                        return Mono.just("{\"type\":\"ERROR\",\"code\":\"INVALID_JSON\",\"message\":\"Invalid JSON payload\"}");
                    }
                })
                .flatMap(responseMsg -> {
                    // Send response back to current session
                    return session.send(Mono.just(session.textMessage(responseMsg)));
                })
                .doOnError(error -> log.error("WebSocket error on session {}", sessionId, error))
                .doFinally(signalType -> connectionManager.remove(sessionId))
                .then();

        Mono<Void> outbound = session.send(
                outboundMessages.map(session::textMessage)
        );

        return Mono.when(inbound, outbound);
    }
}