package com.drawgame.realtime_gateway.websocket;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
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

    private static final Logger log =
            LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ConnectionManager connectionManager;

    public GameWebSocketHandler(
            ConnectionManager connectionManager
    ) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {

        String sessionId = session.getId();

        Flux<String> outboundMessages =
                connectionManager.register(sessionId);

        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)

                .doOnNext(message -> {

                    log.info(
                            "Received from {}: {}",
                            sessionId,
                            message
                    );

                    connectionManager.broadcastExcept(
                            sessionId,
                            message
                    );
                })

                .doOnError(error ->
                        log.error(
                                "WebSocket error. session={}",
                                sessionId,
                                error
                        )
                )

                .doFinally(signalType ->
                        connectionManager.remove(sessionId)
                )

                .then();

        Mono<Void> outbound = session.send(
                outboundMessages
                        .map(session::textMessage)
        );

        return Mono.when(
                inbound,
                outbound
        );
    }
}