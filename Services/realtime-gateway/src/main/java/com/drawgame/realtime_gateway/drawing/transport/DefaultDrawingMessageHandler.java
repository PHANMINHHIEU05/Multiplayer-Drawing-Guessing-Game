package com.drawgame.realtime_gateway.drawing.transport;

import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnMissingBean(name = "customDrawingMessageHandler")
public class DefaultDrawingMessageHandler implements DrawingMessageHandler {

    @Override
    public Mono<Void> handle(DrawingSessionContext session, DrawingMessage message) {
        log.debug("Received drawing message [{}] for session {} (room: {}, player: {})",
                message.opcode(), session.sessionId(), session.roomId(), session.playerId());
        // Boundary for TV3:
        // - drawer authorization
        // - round validation
        // - room broadcast
        // - Redis Pub/Sub
        return Mono.empty();
    }
}
