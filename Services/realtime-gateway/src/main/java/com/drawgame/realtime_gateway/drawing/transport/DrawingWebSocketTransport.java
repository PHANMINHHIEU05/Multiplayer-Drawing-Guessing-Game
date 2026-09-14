package com.drawgame.realtime_gateway.drawing.transport;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.drawing.protocol.BinaryDrawingDecoder;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingOpcode;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingProtocolException;
import com.drawgame.realtime_gateway.security.SessionRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class DrawingWebSocketTransport {

    private static final Logger log = LoggerFactory.getLogger(DrawingWebSocketTransport.class);

    // Max legal binary frame: header + 256 points * 4 bytes + margin
    private static final int MAX_REASONABLE_FRAME_BYTES = 26 + 256 * 4 + 64;

    private final BinaryDrawingDecoder decoder;
    private final DrawingMessageHandler messageHandler;
    private final ConnectionManager connectionManager;
    private final SessionRateLimiter rateLimiter;
    private final int maxFrameBytes;

    public DrawingWebSocketTransport(
            BinaryDrawingDecoder decoder,
            DrawingMessageHandler messageHandler,
            ConnectionManager connectionManager,
            SessionRateLimiter rateLimiter,
            @Value("${security.ws.max-binary-frame-bytes:2048}") int maxFrameBytes
    ) {
        this.decoder = decoder;
        this.messageHandler = messageHandler;
        this.connectionManager = connectionManager;
        this.rateLimiter = rateLimiter;
        this.maxFrameBytes = Math.max(maxFrameBytes, MAX_REASONABLE_FRAME_BYTES);
        log.info("Drawing transport: maxBinaryFrame={}B", this.maxFrameBytes);
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

        // TV8: frame-size ceiling before decoding (codec validates content afterwards)
        if (bytes.length > maxFrameBytes) {
            log.warn("Oversized BINARY drawing frame rejected: session={} bytes={} (max={})",
                    sessionId, bytes.length, maxFrameBytes);
            return Mono.empty();
        }

        // TV8: drawing abuse ceiling — normal ~16ms batching (~60-65/s) is far below this
        if (rateLimiter.tryAcquire(sessionId, SessionRateLimiter.Bucket.DRAW, null) != null) {
            return Mono.empty(); // silently drop the abusive frame; connection stays alive
        }

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
