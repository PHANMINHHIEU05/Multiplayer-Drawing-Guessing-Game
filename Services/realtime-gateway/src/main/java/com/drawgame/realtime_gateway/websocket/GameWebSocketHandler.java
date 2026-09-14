package com.drawgame.realtime_gateway.websocket;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.connection.OutboundFrame;
import com.drawgame.realtime_gateway.drawing.transport.DrawingWebSocketTransport;
import com.drawgame.realtime_gateway.security.SessionRateLimiter;
import com.drawgame.realtime_gateway.websocket.handler.GameCommandHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * TV8 hardening at the WebSocket layer:
 * <ul>
 *   <li>Origin allowlist ({@code security.ws.allowed-origins}) — empty/dev default allows
 *       localhost and non-browser clients; production must set the allowlist.</li>
 *   <li>Max inbound TEXT frame size ({@code security.ws.max-text-frame-bytes}) — oversized
 *       JSON control payloads are rejected before parsing.</li>
 *   <li>Rate-limiter buckets are dropped on disconnect (no growth across cycles).</li>
 * </ul>
 */
@Component
public class GameWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ConnectionManager connectionManager;
    private final GameCommandHandler commandHandler;
    private final DrawingWebSocketTransport drawingTransport;
    private final SessionRateLimiter rateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> allowedOrigins;
    private final int maxTextFrameBytes;

    public GameWebSocketHandler(
            ConnectionManager connectionManager,
            GameCommandHandler commandHandler,
            DrawingWebSocketTransport drawingTransport,
            SessionRateLimiter rateLimiter,
            @Value("${security.ws.allowed-origins:${WS_ALLOWED_ORIGINS:}}") String allowedOrigins,
            @Value("${security.ws.max-text-frame-bytes:${SECURITY_MAX_TEXT_FRAME_BYTES:32768}}") int maxTextFrameBytes
    ) {
        this.connectionManager = connectionManager;
        this.commandHandler = commandHandler;
        this.drawingTransport = drawingTransport;
        this.rateLimiter = rateLimiter;
        this.allowedOrigins = (allowedOrigins == null || allowedOrigins.isBlank())
                ? List.of() // dev default: allow localhost + non-browser clients (see isOriginAllowed)
                : Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.maxTextFrameBytes = maxTextFrameBytes;
        log.info("WebSocket origin allowlist: {} (empty = localhost-only dev mode), maxTextFrame={}B",
                this.allowedOrigins.isEmpty() ? "<dev-localhost>" : this.allowedOrigins, maxTextFrameBytes);
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();

        // TV8: Origin validation at the handshake layer (not REST CORS).
        if (!isOriginAllowed(session)) {
            log.warn("WebSocket handshake REJECTED by origin policy: session={} origin={}",
                    sessionId, originOf(session));
            return Mono.empty(); // reject without accepting the upgrade
        }

        Flux<OutboundFrame> outboundMessages = connectionManager.register(sessionId);

        // WebSocket frames are ordered, and drawing depends on DRAW_START arriving before
        // its DRAW_BATCH/DRAW_END frames. Keep one session's inbound frames ordered instead
        // of allowing concurrent flatMap subscriptions to reorder broadcasts under load.
        Mono<Void> inbound = session.receive()
                .concatMap(message -> {
                    WebSocketMessage.Type type = message.getType();
                    if (type == WebSocketMessage.Type.TEXT) {
                        String rawMessage = message.getPayloadAsText();

                        // TV8: reject oversized control payloads before JSON parsing
                        if (rawMessage.length() > maxTextFrameBytes) {
                            log.warn("Oversized TEXT frame rejected: session={} bytes={} (max={})",
                                    sessionId, rawMessage.length(), maxTextFrameBytes);
                            return session.send(Mono.just(session.textMessage(
                                    "{\"type\":\"ERROR\",\"code\":\"MESSAGE_TOO_LARGE\","
                                            + "\"message\":\"Message exceeds maximum allowed size\"}")));
                        }

                        log.debug("Received TEXT from session {}: {} chars", sessionId, rawMessage.length());
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
                .doFinally(signalType -> {
                    connectionManager.remove(sessionId);
                    rateLimiter.removeSession(sessionId); // TV8: drop limiter buckets
                })
                .then();

        Mono<Void> outbound = session.send(
                outboundMessages.map(frame -> switch (frame) {
                    case OutboundFrame.TextFrame textFrame -> session.textMessage(textFrame.text());
                    case OutboundFrame.BinaryFrame binaryFrame -> session.binaryMessage(factory -> factory.wrap(binaryFrame.bytes()));
                })
        );

        return Mono.when(inbound, outbound);
    }

    /**
     * Origin policy: if an allowlist is configured, only those origins pass.
     * Dev default (empty allowlist): localhost/127.0.0.1 origins and non-browser
     * clients (no Origin header, e.g. server-side tests) are allowed.
     */
    private boolean isOriginAllowed(WebSocketSession session) {
        if (allowedOrigins.isEmpty()) {
            String origin = originOf(session);
            if (origin == null || origin.isBlank()) return true; // non-browser client
            String lower = origin.toLowerCase(Locale.ROOT);
            return lower.startsWith("http://localhost") || lower.startsWith("http://127.0.0.1")
                    || lower.startsWith("https://localhost") || lower.startsWith("https://127.0.0.1");
        }
        String origin = originOf(session);
        if (origin == null) return false; // allowlist configured → require a known origin
        return allowedOrigins.contains(origin);
    }

    private String originOf(WebSocketSession session) {
        HttpHeaders headers = session.getHandshakeInfo().getHeaders();
        return headers.getOrigin();
    }
}
