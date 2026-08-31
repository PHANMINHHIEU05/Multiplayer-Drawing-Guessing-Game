package com.drawgame.realtime_gateway.drawing.transport;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.drawing.protocol.BinaryDrawingDecoder;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingOpcode;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class DrawingWebSocketTransport {

    private static final Logger log = LoggerFactory.getLogger(DrawingWebSocketTransport.class);

    private final BinaryDrawingDecoder decoder;
    private final DrawingMessageHandler messageHandler;
    private final ConnectionManager connectionManager;

    public DrawingWebSocketTransport(
            BinaryDrawingDecoder decoder,
            DrawingMessageHandler messageHandler,
            ConnectionManager connectionManager
    ) {
        this.decoder = decoder;
        this.messageHandler = messageHandler;
        this.connectionManager = connectionManager;
    }

    public Mono<Void> handleBinaryMessage(WebSocketSession session, WebSocketMessage webSocketMessage) {
        String sessionId = session.getId();
        DataBuffer dataBuffer = webSocketMessage.getPayload();
        byte[] bytes;
        // WebFlux/Netty owns the inbound WebSocketMessage lifecycle and releases its
        // native frame after this receive callback. Copy the payload synchronously, but
        // do not release it here or the frame will be released twice.
        bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);

        DrawingMessage drawingMessage;
        try {
            drawingMessage = decoder.decode(bytes);
        } catch (DrawingProtocolException e) {
            String opcodeStr = extractOpcodeForLogging(bytes);
            log.warn("Malformed binary drawing frame from session {}: length={}, opcode={}, errorCode={}, error={}",
                    sessionId, bytes.length, opcodeStr, e.getErrorCode(), e.getMessage());
            return Mono.empty();
        } catch (Exception e) {
            String opcodeStr = extractOpcodeForLogging(bytes);
            log.warn("Failed to decode binary drawing frame from session {}: length={}, opcode={}, error={}",
                    sessionId, bytes.length, opcodeStr, e.getMessage());
            return Mono.empty();
        }

        String roomId = connectionManager.getRoomId(sessionId);
        String playerId = connectionManager.getPlayerId(sessionId);
        DrawingSessionContext context = new DrawingSessionContext(sessionId, roomId, playerId);

        return messageHandler.handle(context, drawingMessage)
                .doOnError(error -> log.error("Error handling drawing message [{}] for session {}",
                        drawingMessage.opcode(), sessionId, error))
                .onErrorResume(error -> Mono.empty());
    }

    private String extractOpcodeForLogging(byte[] bytes) {
        if (bytes != null && bytes.length >= 2) {
            try {
                return DrawingOpcode.fromCode(Byte.toUnsignedInt(bytes[1])).name();
            } catch (Exception ignored) {
            }
        }
        return "UNKNOWN";
    }
}
