package com.drawgame.realtime_gateway.drawing.transport;

import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface DrawingMessageHandler {

    Mono<Void> handle(
            DrawingSessionContext session,
            DrawingMessage message
    );
}
