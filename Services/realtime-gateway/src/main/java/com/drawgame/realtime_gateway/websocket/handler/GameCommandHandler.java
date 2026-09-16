package com.drawgame.realtime_gateway.websocket.handler;

import com.drawgame.chat.grpc.generated.ChatMessageResponse;
import com.drawgame.game.grpc.generated.GameStateResponse;
import com.drawgame.game.grpc.generated.PlayerScoreMessage;
import com.drawgame.realtime_gateway.connection.BoundedOutboundQueue;
import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.control.ControlEventRouter;
import com.drawgame.realtime_gateway.drawing.recovery.DrawingRecoveryRepository;
import com.drawgame.realtime_gateway.drawing.routing.DrawingRoomState;
import com.drawgame.realtime_gateway.drawing.routing.DrawingRoomStateCache;
import com.drawgame.realtime_gateway.security.GameSessionTokenService;
import com.drawgame.realtime_gateway.security.InputValidator;
import com.drawgame.realtime_gateway.security.SessionRateLimiter;
import com.drawgame.realtime_gateway.grpc.ChatGrpcClient;
import com.drawgame.realtime_gateway.grpc.GameGrpcClient;
import com.drawgame.realtime_gateway.grpc.RoomGrpcClient;
import com.drawgame.room.grpc.generated.PlayerMessage;
import com.drawgame.room.grpc.generated.RoomResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GameCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(GameCommandHandler.class);

    private final GameGrpcClient gameGrpcClient;
    private final RoomGrpcClient roomGrpcClient;
    private final ChatGrpcClient chatGrpcClient;
    private final ConnectionManager connectionManager;
    private final DrawingRoomStateCache drawingRoomStateCache;
    private final ControlEventRouter controlEventRouter;
    private final DrawingRecoveryRepository recoveryRepository;
    private final ObjectMapper objectMapper;
    private final String gatewayInstanceId;
    /** TV8 security: signed game-session credentials, per-session rate limits, input bounds. */
    private final GameSessionTokenService tokenService;
    private final SessionRateLimiter rateLimiter;
    private final InputValidator inputValidator;

    public GameCommandHandler(
            GameGrpcClient gameGrpcClient,
            RoomGrpcClient roomGrpcClient,
            ChatGrpcClient chatGrpcClient,
            ConnectionManager connectionManager,
            DrawingRoomStateCache drawingRoomStateCache
    ) {
        this(gameGrpcClient, roomGrpcClient, chatGrpcClient, connectionManager, drawingRoomStateCache,
                null, null, null, null, null, "gateway-default");
    }

    public GameCommandHandler(
            GameGrpcClient gameGrpcClient,
            RoomGrpcClient roomGrpcClient,
            ChatGrpcClient chatGrpcClient,
            ConnectionManager connectionManager,
            DrawingRoomStateCache drawingRoomStateCache,
            @org.springframework.beans.factory.annotation.Value("${gateway.instance-id:gateway-1}") String gatewayInstanceId
    ) {
        this(gameGrpcClient, roomGrpcClient, chatGrpcClient, connectionManager, drawingRoomStateCache,
                null, null, null, null, null, gatewayInstanceId);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GameCommandHandler(
            GameGrpcClient gameGrpcClient,
            RoomGrpcClient roomGrpcClient,
            ChatGrpcClient chatGrpcClient,
            ConnectionManager connectionManager,
            DrawingRoomStateCache drawingRoomStateCache,
            ControlEventRouter controlEventRouter,
            DrawingRecoveryRepository recoveryRepository,
            GameSessionTokenService tokenService,
            SessionRateLimiter rateLimiter,
            InputValidator inputValidator,
            @org.springframework.beans.factory.annotation.Value("${gateway.instance-id:gateway-1}") String gatewayInstanceId
    ) {
        this.gameGrpcClient = gameGrpcClient;
        this.roomGrpcClient = roomGrpcClient;
        this.chatGrpcClient = chatGrpcClient;
        this.connectionManager = connectionManager;
        this.drawingRoomStateCache = drawingRoomStateCache;
        this.controlEventRouter = controlEventRouter;
        this.recoveryRepository = recoveryRepository;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.inputValidator = inputValidator;
        this.objectMapper = new ObjectMapper();
        this.gatewayInstanceId = gatewayInstanceId;
    }

    public Mono<String> handleCommand(String sessionId, JsonNode json) {
        String type = json.has("type") ? json.get("type").asText() : "";
        String requestId = extractRequestId(json);
        if ("PING".equalsIgnoreCase(type) || "APP_PING".equalsIgnoreCase(type)) {
            log.trace("Handling heartbeat '{}' from session {}", type, sessionId);
        } else {
            log.info("Handling command type '{}' (reqId: {}) from session {}", type, requestId, sessionId);
        }

        // TV8: per-session abuse protection. Heartbeats are never limited (2s cadence,
        // tiny payload). Drawing binary frames are limited in the binary transport path.
        if (rateLimiter != null && !"PING".equalsIgnoreCase(type) && !"APP_PING".equalsIgnoreCase(type)) {
            SessionRateLimiter.Bucket bucket = switch (type) {
                case "SUBMIT_GUESS", "SEND_CHAT" -> SessionRateLimiter.Bucket.GUESS;
                case "DRAW_POINT", "DRAW_BATCH", "CLEAR_CANVAS" -> SessionRateLimiter.Bucket.DRAW;
                default -> SessionRateLimiter.Bucket.CONTROL;
            };
            String limited = rateLimiter.tryAcquire(sessionId, bucket, requestId);
            if (limited != null) {
                return Mono.just(limited);
            }
        }

        return switch (type) {
            case "PING", "APP_PING" -> handlePing(sessionId, json, requestId);
            case "CREATE_ROOM" -> handleCreateRoom(sessionId, json, requestId);
            case "JOIN_ROOM" -> handleJoinRoom(sessionId, json, requestId);
            case "RESUME_SESSION" -> handleResumeSession(sessionId, json, requestId);
            case "GET_ROOM" -> handleGetRoom(sessionId, json, requestId);
            case "LEAVE_ROOM" -> handleLeaveRoom(sessionId, json, requestId);
            case "START_GAME" -> handleStartGame(sessionId, json, requestId);
            case "GET_GAME_STATE" -> handleGetGameState(sessionId, json, requestId);
            case "SUBMIT_GUESS" -> handleSubmitGuess(sessionId, json, requestId);
            case "GET_CANVAS_STATE" -> handleGetCanvasState(sessionId, json, requestId);
            // TV10: lobby/product features — all derive identity from the bound session
            case "SET_READY" -> handleSetReady(sessionId, json, requestId);
            case "REMATCH" -> handleRematch(sessionId, json, requestId);
            case "KICK_PLAYER" -> handleKickPlayer(sessionId, json, requestId);
            case "SEND_CHAT" -> handleSendChat(sessionId, json, requestId);
            case "GET_RECENT_CHAT" -> handleGetRecentChat(sessionId, json, requestId);
            case "DRAW_POINT" -> handleDrawPoint(sessionId, json);
            case "DRAW_BATCH" -> handleDrawBatch(sessionId, json);
            case "CLEAR_CANVAS" -> handleClearCanvas(sessionId, json);
            // TV3: clear drawing cache when game finishes
            case "GAME_FINISHED" -> handleGameFinished(sessionId, json, requestId);
            default -> Mono.just(createErrorJson(requestId, "UNKNOWN_COMMAND", "Unknown command type: " + type));
        };
    }

    private Mono<String> handlePing(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        long clientTimestamp = 0L;
        if (node.has("timestamp")) {
            clientTimestamp = node.get("timestamp").asLong();
        } else if (node.has("sentAt")) {
            clientTimestamp = node.get("sentAt").asLong();
        } else if (node.has("clientTimestamp")) {
            clientTimestamp = node.get("clientTimestamp").asLong();
        } else {
            clientTimestamp = System.currentTimeMillis();
        }

        if (connectionManager.getGatewayMetrics() != null) {
            connectionManager.getGatewayMetrics().incrementHeartbeatPingReceived();
            connectionManager.getGatewayMetrics().incrementHeartbeatPongSent();
        }

        BoundedOutboundQueue queue = connectionManager.getQueueForSession(sessionId);
        int queueSize = (queue != null) ? queue.getCurrentQueueSize() : 0;

        Map<String, Object> map = new HashMap<>();
        map.put("type", "APP_PONG");
        if (requestId != null && !requestId.isBlank()) {
            map.put("requestId", requestId);
        }
        map.put("clientTimestamp", clientTimestamp);
        map.put("serverTimestamp", System.currentTimeMillis());
        map.put("queueSize", queueSize);
        map.put("gatewayId", gatewayInstanceId);

        return Mono.just(toJson(map));
    }

    private Mono<String> handleCreateRoom(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        // TV8: CREATE/JOIN is the identity-establishing flow — the client still names
        // itself here (MVP has no accounts), but the nickname is sanitized and the
        // server immediately binds the session and issues a signed credential.
        String playerId = extractString(node, "playerId", sessionId);
        String username = sanitizeOr(inputValidator, extractString(node, "username", ""),
                "Player-" + sessionId.substring(0, Math.min(6, sessionId.length())));
        String roomName = sanitizeOr(inputValidator, extractString(node, "roomName", extractString(node, "name", "")),
                username + "'s Room");
        int maxPlayers = node.has("maxPlayers") ? node.get("maxPlayers").asInt() : (node.has("max_players") ? node.get("max_players").asInt() : 4);
        int totalRounds = node.has("totalRounds") ? node.get("totalRounds").asInt() : (node.has("roundCount") ? node.get("roundCount").asInt() : 5);
        int roundDuration = node.has("roundDuration") ? node.get("roundDuration").asInt() : (node.has("drawTime") ? node.get("drawTime").asInt() : 60);
        // TV8: bound room configuration (2..12 players, 1..20 rounds, 15..300s rounds)
        maxPlayers = Math.max(2, Math.min(12, maxPlayers));
        totalRounds = Math.max(1, Math.min(20, totalRounds));
        roundDuration = Math.max(15, Math.min(300, roundDuration));

        return roomGrpcClient.createRoom(playerId, username, roomName, maxPlayers, totalRounds, roundDuration)
                .map(response -> {
                    connectionManager.bindSession(sessionId, response.getRoomId(), playerId, username);
                    // TV8: issue signed game-session credential after membership is authoritative
                    String json2 = createRoomSuccessJson("ROOM_CREATED", response, requestId);
                    return withSessionToken(json2, playerId, response.getRoomId());
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "ROOM_CREATE_FAILED", e.getMessage())));
    }

    /** Null-safe sanitizer helper — falls back to the default when input is invalid. */
    private String sanitizeOr(InputValidator validator, String raw, String fallback) {
        if (validator == null) {
            return (raw == null || raw.isBlank()) ? fallback : raw;
        }
        if (raw == null || raw.isBlank()) return fallback;
        return validator.sanitizeNickname(raw) != null ? raw.strip() : fallback;
    }

    private Mono<String> handleJoinRoom(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = extractString(node, "roomId", "");
        String playerId = extractString(node, "playerId", sessionId);
        String username = extractString(node, "username", "Player-" + sessionId);

        // TV8: input validation before any gRPC call
        if (inputValidator != null) {
            if (!inputValidator.isValidRoomCode(roomId)) {
                return Mono.just(createErrorJson(requestId, "INVALID_ROOM_CODE", "Room code must be 4-32 uppercase letters/digits"));
            }
            String sanitized = inputValidator.sanitizeNickname(username);
            if (sanitized == null) {
                return Mono.just(createErrorJson(requestId, "INVALID_NICKNAME",
                        "Nickname must be 1-32 characters without control characters"));
            }
            username = sanitized;
        }
        final String validatedUsername = username;

        return roomGrpcClient.joinRoom(roomId, playerId, validatedUsername)
                .map(response -> {
                    connectionManager.bindSession(sessionId, response.getRoomId(), playerId, validatedUsername);
                    String responseJson = createRoomSuccessJson("ROOM_JOINED", response, requestId);
                    // TV8: issue signed game-session credential after membership succeeds
                    responseJson = withSessionToken(responseJson, playerId, response.getRoomId());
                    // TV6: room-scoped control event — local broadcast + Redis fanout to other Gateways
                    controlBroadcast(response.getRoomId(), sessionId, "PLAYER_JOINED",
                            createBroadcastJson("PLAYER_JOINED", response.getRoomId(), playerId, validatedUsername));
                    return responseJson;
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "ROOM_JOIN_FAILED", e.getMessage())));
    }

    /**
     * Re-bind a new WebSocket session to an existing Room Service membership.
     * This must not call JOIN_ROOM: reconnecting is not a new room membership.
     */
    private Mono<String> handleResumeSession(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = extractString(node, "roomId", "");
        String clientPlayerId = extractString(node, "playerId", "");
        String token = extractString(node, "token", "");

        if (roomId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "roomId is required to resume a session"));
        }

        // TV8 SECURITY: the signed game-session token is the ONLY proof of identity.
        // The client-supplied playerId field is compatibility-only: if present it must
        // MATCH the verified subject, and it is never trusted on its own. A raw
        // playerId without a valid token is rejected (no insecure legacy fallback).
        if (tokenService == null) {
            // Legacy unit-test constructor — cannot authenticate
            return Mono.just(createErrorJson(requestId, "AUTH_REQUIRED", "Authentication unavailable"));
        }
        GameSessionTokenService.Verification verification = tokenService.verify(token, roomId);
        if (!(verification instanceof GameSessionTokenService.Verification.Verified verified)) {
            String code = ((GameSessionTokenService.Verification.Invalid) verification).code();
            log.info("RESUME denied: session={} room={} code={}", sessionId, roomId, code);
            return Mono.just(createErrorJson(requestId, code,
                    ((GameSessionTokenService.Verification.Invalid) verification).detail()));
        }
        String playerId = verified.playerId(); // authoritative identity from verified claims
        if (!clientPlayerId.isBlank() && !clientPlayerId.equals(playerId)) {
            // Payload playerId conflicts with the credential — the credential wins.
            log.warn("RESUME playerId mismatch rejected: session={} tokenSub={} payloadPlayerId={}",
                    sessionId, playerId, clientPlayerId);
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION_TOKEN",
                    "Credential subject does not match payload playerId"));
        }

        return roomGrpcClient.getRoom(roomId)
                .map(room -> {
                    // Authoritative membership check — a cryptographically valid old token
                    // cannot resurrect deleted membership (explicit leave / room deleted).
                    boolean isMember = room.getPlayersList().stream()
                            .map(PlayerMessage::getPlayerId)
                            .anyMatch(playerId::equals);

                    if (!isMember) {
                        return createErrorJson(
                                requestId,
                                "PLAYER_NOT_IN_ROOM",
                                "Player is not a member of room: " + roomId
                        );
                    }

                    String resumedUsername = room.getPlayersList().stream()
                            .filter(p -> playerId.equals(p.getPlayerId()))
                            .findFirst()
                            .map(PlayerMessage::getUsername)
                            .orElse("Người chơi");

                    connectionManager.bindSession(sessionId, roomId, playerId, resumedUsername);

                    // TV7 (stale-session replacement, spec §41): notify OTHER Gateways that
                    // this player now lives HERE — they evict any old binding for the same
                    // player+room so the old session stops receiving room broadcasts.
                    // This only runs AFTER credential + membership validation succeeded,
                    // so an attacker cannot evict a victim without the victim's token.
                    if (controlEventRouter != null) {
                        Map<String, Object> evict = new HashMap<>();
                        evict.put("type", "PLAYER_SESSION_REPLACED");
                        evict.put("roomId", roomId);
                        evict.put("playerId", playerId);
                        controlEventRouter.broadcastToRoom(roomId, "PLAYER_SESSION_REPLACED", toJson(evict));

                        Map<String, Object> reconnected = new HashMap<>();
                        reconnected.put("type", "PLAYER_RECONNECTED");
                        reconnected.put("roomId", roomId);
                        reconnected.put("playerId", playerId);
                        reconnected.put("username", resumedUsername);
                        controlEventRouter.broadcastToRoom(roomId, "PLAYER_RECONNECTED", toJson(reconnected));
                    }

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("playerId", playerId);
                    payload.put("roomId", roomId);
                    payload.put("roomStatus", room.getStatus());

                    Map<String, Object> response = new HashMap<>();
                    response.put("type", "SESSION_RESUMED");
                    if (requestId != null && !requestId.isBlank()) {
                        response.put("requestId", requestId);
                    }
                    response.put("payload", payload);
                    // TV8: rotate the credential on successful resume (fresh expiry)
                    response.put("sessionToken", tokenService.issue(playerId, roomId));
                    return toJson(response);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(
                        requestId,
                        mapResumeErrorCode(e),
                        e.getMessage() != null ? e.getMessage() : "Unable to resume session"
                )));
    }

    /**
     * TV8: attach a freshly issued signed game-session token to a CREATE/JOIN response.
     * The token is the resume credential — claims sub=playerId, room=roomId, purpose=GAME_SESSION.
     */
    private String withSessionToken(String responseJson, String playerId, String roomId) {
        if (tokenService == null) {
            return responseJson; // legacy unit-test constructor
        }
        try {
            JsonNode tree = objectMapper.readTree(responseJson);
            if (tree.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) tree)
                        .put("sessionToken", tokenService.issue(playerId, roomId));
                return objectMapper.writeValueAsString(tree);
            }
        } catch (Exception e) {
            log.warn("Failed to attach session token to response: {}", e.getMessage());
        }
        return responseJson;
    }

    /**
     * TV10 READY: lobby readiness toggle. Bound-session identity only — a payload
     * playerId can never toggle someone else's readiness. Broadcasts
     * PLAYER_READY_CHANGED to the whole room (cross-Gateway via control fanout).
     */
    private Mono<String> handleSetReady(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }
        final boolean ready = node.has("ready") && node.get("ready").asBoolean(false);

        return roomGrpcClient.setReady(roomId, playerId, ready)
                .map(room -> {
                    // room-wide readiness update (includes full player list with ready flags)
                    controlBroadcast(roomId, null, "PLAYER_READY_CHANGED",
                            createRoomSuccessJson("PLAYER_READY_CHANGED", room, null));
                    return createRoomSuccessJson("ROOM_INFO", room, requestId);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "SET_READY_FAILED", e.getMessage())));
    }

    /**
     * TV10 REMATCH: host-only FINISHED -> WAITING. Room/members/config preserved;
     * ready state cleared. Old match result already persisted by Game Service.
     * Also defensively clears drawing auth cache + canvas recovery state on every
     * Gateway (cross-Gateway control event does the same on remote gateways).
     */
    private Mono<String> handleRematch(String sessionId, JsonNode json, String requestId) {
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }

        return roomGrpcClient.resetRoom(roomId, playerId)
                .map(room -> {
                    // local defensive cleanup (remote gateways get it via the control event below)
                    drawingRoomStateCache.remove(roomId);
                    if (recoveryRepository != null) {
                        recoveryRepository.removeAll(roomId).subscribe();
                    }
                    // room-wide reset: every client on every Gateway returns to Lobby
                    controlBroadcast(roomId, null, "ROOM_RESET",
                            createRoomSuccessJson("ROOM_RESET", room, null));
                    return createRoomSuccessJson("ROOM_INFO", room, requestId);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "REMATCH_FAILED", e.getMessage())));
    }

    /**
     * TV10 KICK: host-only (WAITING-only) removal of another member. The kicked
     * player may be connected to ANOTHER Gateway — the room-wide PLAYER_KICKED
     * control event reaches them there; the frontend detects it targets them and
     * exits to Home. Stale JWT resume afterwards fails via membership check.
     */
    private Mono<String> handleKickPlayer(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = connectionManager.getRoomId(sessionId);
        final String requesterId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || requesterId == null || requesterId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }
        final String targetPlayerId = extractString(node, "targetPlayerId", "");
        if (targetPlayerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_KICK", "targetPlayerId is required"));
        }
        if (targetPlayerId.equals(requesterId)) {
            return Mono.just(createErrorJson(requestId, "CANNOT_KICK_SELF", "Host cannot kick themselves — use Leave"));
        }

        return roomGrpcClient.kickPlayer(roomId, requesterId, targetPlayerId)
                .map(room -> {
                    // room-wide event carries the target; the target's client reacts and exits
                    Map<String, Object> kickEvent = new HashMap<>();
                    kickEvent.put("type", "PLAYER_KICKED");
                    kickEvent.put("roomId", roomId);
                    kickEvent.put("targetPlayerId", targetPlayerId);
                    kickEvent.put("byHost", requesterId);
                    kickEvent.put("players", room.getPlayersList().stream()
                            .map(p -> Map.of("playerId", p.getPlayerId(), "username", p.getUsername(), "ready", p.getReady()))
                            .collect(java.util.stream.Collectors.toList()));
                    controlBroadcast(roomId, null, "PLAYER_KICKED", toJson(kickEvent));
                    // updated room state for remaining members
                    controlBroadcast(roomId, null, "PLAYER_LEFT",
                            createBroadcastJson("PLAYER_LEFT", roomId, targetPlayerId, ""));
                    return toJson(Map.of("type", "KICK_OK", "requestId", requestId == null ? "" : requestId));
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "KICK_FAILED", e.getMessage())));
    }

    private Mono<String> handleGetRoom(String sessionId, JsonNode json, String requestId) {
        // TV8: room comes from the bound session — a session cannot probe other rooms.
        // (Unbound sessions get an explicit error rather than arbitrary room dumps.)
        String roomId = connectionManager.getRoomId(sessionId);
        if (roomId == null || roomId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }

        return roomGrpcClient.getRoom(roomId)
                .map(response -> createRoomSuccessJson("ROOM_INFO", response, requestId))
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "GET_ROOM_FAILED", e.getMessage())));
    }

    private Mono<String> handleLeaveRoom(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        // TV8: bound room + AUTHENTICATED session identity — client playerId is ignored
        String roomId = connectionManager.getRoomId(sessionId);
        String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }

        final String leavingUsername = extractString(node, "username", "");
        final String storedUsername = connectionManager.getUsername(sessionId);
        final String resolvedLeavingUsername = !leavingUsername.isBlank() ? leavingUsername
                : (storedUsername != null && !storedUsername.isBlank() ? storedUsername : "Người chơi");

        return roomGrpcClient.leaveRoom(roomId, playerId)
                .map(response -> {
                    String responseJson = createRoomSuccessJson("ROOM_LEFT", response, requestId);

                    // TV6 + QA fix: PLAYER_LEFT control event — broadcast BEFORE unbinding
                    // so other local room members still receive it, and fan out to remote Gateways.
                    // Payload carries leaving username, updated hostPlayerId, and remaining players list.
                    Map<String, Object> leftPayload = new HashMap<>();
                    leftPayload.put("type", "PLAYER_LEFT");
                    leftPayload.put("roomId", roomId);
                    leftPayload.put("playerId", playerId);
                    leftPayload.put("username", resolvedLeavingUsername);
                    leftPayload.put("hostPlayerId", response.getHostId());
                    leftPayload.put("players", response.getPlayersList().stream()
                            .map(p -> Map.of(
                                    "playerId", p.getPlayerId(),
                                    "username", p.getUsername(),
                                    "ready", p.getReady()
                            ))
                            .collect(java.util.stream.Collectors.toList()));
                    String leftBroadcastJson = toJson(leftPayload);
                    controlBroadcast(roomId, sessionId, "PLAYER_LEFT", leftBroadcastJson);

                    // TV3 Stabilization (GW-07): unbind session from room routing so this session
                    // no longer receives drawing events or passes drawing authorization checks.
                    connectionManager.unbindSession(sessionId);
                    // TV3 Stabilization: evict drawing cache if room is now empty or game ended.
                    // Safe to call even if cache entry doesn't exist.
                    if (response.getPlayersList().isEmpty()) {
                        drawingRoomStateCache.remove(roomId);
                        log.info("DrawingRoomStateCache evicted — last player left room={}", roomId);
                    }
                    return responseJson;
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "LEAVE_ROOM_FAILED", e.getMessage())));
    }

    private Mono<String> handleStartGame(String sessionId, JsonNode json, String requestId) {
        // TV8: bound room + AUTHENTICATED session identity (host check happens in
        // Room/Game Service against authoritative state — a non-host is rejected there)
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }

        return gameGrpcClient.startGame(roomId, playerId)
                .map(gameState -> {
                    String stateJson = createGameStateJson("GAME_STARTED", gameState, requestId);
                    // TV6: GAME_STARTED — local broadcast (no secretWord: response is stripped by
                    // Game Service) + Redis fanout so remote-Gateway clients enter the game too.
                    controlBroadcast(roomId, sessionId, "GAME_STARTED",
                            createGameStateJson("GAME_STARTED", gameState, null));
                    // TV3: update drawing fast-path cache with the new drawer and round
                    updateDrawingCache(roomId, gameState);
                    return stateJson;
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "START_GAME_FAILED", e.getMessage())));
    }

    private Mono<String> handleGetGameState(String sessionId, JsonNode json, String requestId) {
        // TV8: bound room + AUTHENTICATED session identity — viewer identity cannot be spoofed
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }

        return gameGrpcClient.getGameState(roomId, playerId)
                .map(gameState -> {
                    // TV3: sync drawing fast-path cache on GET_GAME_STATE (handles cache miss after restart)
                    if ("PLAYING".equalsIgnoreCase(gameState.getStatus())) {
                        updateDrawingCache(roomId, gameState);
                    }
                    return createGameStateJson("GAME_STATE", gameState, requestId);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "GET_GAME_STATE_FAILED", e.getMessage())));
    }

    /**
     * TV7 — current-round Canvas recovery (doc/reconnect-canvas-recovery.md §6).
     *
     * <p>Validates: session bound to a room+player (via RESUME_SESSION/JOIN), an active
     * PLAYING game, and the requested round matching the authoritative current round.
     * Reads the shared Redis Stream so ANY Gateway can serve recovery. Responds with
     * SYNC_CANVAS_STATE carrying drawing events ONLY (never secretWord/game internals).
     */
    private Mono<String> handleGetCanvasState(String sessionId, JsonNode json, String requestId) {
        if (recoveryRepository == null) {
            // Legacy unit-test constructor — recovery not wired
            return Mono.just(createErrorJson(requestId, "RECOVERY_NOT_AVAILABLE", "Canvas recovery not available"));
        }

        JsonNode node = getPayloadOrRoot(json);
        // Identity comes from the BOUND session context — client-supplied ids are ignored
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room/player"));
        }

        final int requestedRound = node.has("round") ? node.get("round").asInt(-1) : -1;
        if (requestedRound < 0) {
            return Mono.just(createErrorJson(requestId, "INVALID_RECOVERY_REQUEST", "round is required"));
        }

        // Authoritative round check via Game Service (cache may be stale on this Gateway)
        return gameGrpcClient.getGameState(roomId, playerId)
                .flatMap(gameState -> {
                    if (!"PLAYING".equalsIgnoreCase(gameState.getStatus())) {
                        return Mono.just(createErrorJson(requestId, "GAME_NOT_ACTIVE",
                                "Game is not active: " + gameState.getStatus()));
                    }
                    if (gameState.getCurrentRound() != requestedRound) {
                        return Mono.just(createErrorJson(requestId, "WRONG_ROUND",
                                "Requested round " + requestedRound + " is not the active round "
                                        + gameState.getCurrentRound()));
                    }
                    // keep the fast-path cache fresh for this Gateway too
                    updateDrawingCache(roomId, gameState);

                    return recoveryRepository.readHistory(roomId, requestedRound)
                            .map(result -> createCanvasStateJson(requestId, roomId, requestedRound, result))
                            .onErrorResume(DrawingRecoveryRepository.RecoveryUnavailableException.class,
                                    e -> Mono.just(createErrorJson(requestId, "RECOVERY_NOT_AVAILABLE",
                                            "Canvas recovery temporarily unavailable")));
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "GET_GAME_STATE_FAILED", e.getMessage())));
    }

    /** SYNC_CANVAS_STATE response — drawing events only, never secret word or game internals. */
    private String createCanvasStateJson(String requestId, String roomId, int round,
                                         DrawingRecoveryRepository.RecoveryResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("roomId", roomId);
        payload.put("round", round);
        payload.put("mode", "EVENT_REPLAY");
        payload.put("historyComplete", result.historyComplete);
        payload.put("lastStreamId", result.lastStreamId);
        payload.put("events", result.events);

        Map<String, Object> map = new HashMap<>();
        map.put("type", "SYNC_CANVAS_STATE");
        if (requestId != null && !requestId.isBlank()) {
            map.put("requestId", requestId);
        }
        map.put("payload", payload);
        return toJson(map);
    }

    private Mono<String> handleSubmitGuess(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        // TV8: AUTHENTICATED session identity — a payload playerId cannot submit on
        // behalf of another player. Score/GUESS_RESULT always apply to this identity.
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }
        final String username = extractString(node, "username", ""); // display only, never identity
        String guess = extractString(node, "guess", extractString(node, "content", ""));

        // TV8: guess input bounds (Vietnamese Unicode preserved; Game Service matching unchanged)
        if (inputValidator != null) {
            String sanitized = inputValidator.sanitizeGuess(guess);
            if (sanitized == null) {
                return Mono.just(createErrorJson(requestId, "INVALID_GUESS",
                        "Guess must be 1-128 characters without control characters"));
            }
            guess = sanitized;
        }
        final String validatedGuess = guess;

        return gameGrpcClient.submitGuess(roomId, playerId, guess)
                .flatMap(response -> {
                    String status = response.getGuessStatus();
                    if ("CORRECT".equalsIgnoreCase(status)) {
                        // TV6: PLAYER_GUESSED_CORRECTLY is room-scoped — local + Redis fanout.
                        // Payload intentionally contains NO answer text (only playerId + score).
                        String broadcastMsg = createGuessCorrectBroadcastJson(roomId, playerId, response.getScoreAwarded());
                        controlBroadcast(roomId, sessionId, "PLAYER_GUESSED_CORRECTLY", broadcastMsg);

                        Map<String, Object> map = createGuessResultMap(roomId, playerId, status, response.getScoreAwarded(), requestId);

                        // TV3 Stabilization (GW-05/GW-06): a correct guess may trigger round transition.
                        // Refresh drawing cache so new drawer/round is authoritative without delay.
                        // Failure to refresh is non-fatal — next GET_GAME_STATE will re-sync.
                        return gameGrpcClient.getGameState(roomId, playerId)
                                .doOnNext(gameState -> {
                                    if ("PLAYING".equalsIgnoreCase(gameState.getStatus())) {
                                        updateDrawingCache(roomId, gameState);
                                        log.info("DrawingRoomStateCache refreshed after CORRECT guess: room={} drawer={} round={}",
                                                roomId, gameState.getDrawerId(), gameState.getCurrentRound());
                                    } else {
                                        // Game finished after last round
                                        drawingRoomStateCache.remove(roomId);
                                        log.info("DrawingRoomStateCache evicted — game ended after CORRECT guess: room={} status={}",
                                                roomId, gameState.getStatus());
                                    }
                                })
                                .onErrorResume(e -> {
                                    log.warn("Failed to refresh drawing cache after CORRECT guess: room={} — will resync on next GET_GAME_STATE: {}",
                                            roomId, e.getMessage());
                                    return reactor.core.publisher.Mono.empty();
                                })
                                .thenReturn(toJson(map));
                    } else {
                        // BUG-1 fix: Guess input and Chat must remain completely separate.
                        // Non-CORRECT guesses (WRONG, CLOSE, etc.) are private feedback to the submitter
                        // and MUST NOT be forwarded to Chat Service or broadcast as CHAT_MESSAGE.
                        Map<String, Object> map = createGuessResultMap(roomId, playerId, status, response.getScoreAwarded(), requestId);
                        return Mono.just(toJson(map));
                    }
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "SUBMIT_GUESS_FAILED", e.getMessage())));
    }

    private Mono<String> handleSendChat(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        // TV8: AUTHENTICATED session identity — chat sender cannot be spoofed.
        // Chat Service resolves the authoritative username from room membership.
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }
        final String content = extractString(node, "content", "");
        // TV8: chat length is bounded server-side; Chat Service re-validates + rate-limits

        return chatGrpcClient.sendMessage(roomId, playerId, "", content)
                .map(chatRes -> {
                    String chatBroadcastJson = createChatMessageBroadcastJson(chatRes, null);
                    // TV6: CHAT_MESSAGE is room-scoped — local broadcast + Redis fanout
                    controlBroadcast(roomId, null, "CHAT_MESSAGE", chatBroadcastJson);
                    return createChatMessageBroadcastJson(chatRes, requestId);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, mapGrpcErrorCode(e), e.getMessage())));
    }

    private Mono<String> handleGetRecentChat(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        // TV8: bound identity + bounded limit
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }
        final int limit = Math.max(1, Math.min(100, node.has("limit") ? node.get("limit").asInt() : 50));

        return chatGrpcClient.getRecentMessages(roomId, playerId, limit)
                .map(res -> {
                    List<Map<String, Object>> messages = new ArrayList<>();
                    for (ChatMessageResponse msg : res.getMessagesList()) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("messageId", msg.getMessageId());
                        m.put("roomId", msg.getRoomId());
                        m.put("playerId", msg.getPlayerId());
                        m.put("username", msg.getUsername());
                        m.put("content", msg.getContent());
                        m.put("type", msg.getType());
                        m.put("createdAt", msg.getCreatedAtEpochMs());
                        messages.add(m);
                    }

                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "CHAT_HISTORY");
                    if (requestId != null && !requestId.isBlank()) {
                        map.put("requestId", requestId);
                    }
                    map.put("roomId", roomId);
                    map.put("messages", messages);
                    return toJson(map);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, mapGrpcErrorCode(e), e.getMessage())));
    }

    private Mono<String> handleDrawPoint(String sessionId, JsonNode json) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        if (roomId == null || roomId.isBlank() || !node.has("point")) {
            return Mono.empty();
        }
        if (!isAuthorizedDrawer(sessionId, roomId)) {
            return Mono.empty();
        }

        Map<String, Object> event = new HashMap<>();
        event.put("type", "DRAW_EVENT");
        event.put("roomId", roomId);
        event.put("playerId", extractString(node, "drawerId", connectionManager.getPlayerId(sessionId)));
        event.put("point", node.get("point"));
        // TV6: JSON drawing fallback path — room-scoped control fanout (binary path uses
        // DrawingRedisPublisher and is unchanged).
        controlBroadcast(roomId, sessionId, "DRAW_EVENT", toJson(event));
        return Mono.empty();
    }

    private Mono<String> handleDrawBatch(String sessionId, JsonNode json) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        if (roomId == null || roomId.isBlank() || !node.has("points") || !node.get("points").isArray()) {
            return Mono.empty();
        }
        if (!isAuthorizedDrawer(sessionId, roomId)) {
            return Mono.empty();
        }

        Map<String, Object> event = new HashMap<>();
        event.put("type", "DRAW_BATCH_EVENT");
        event.put("roomId", roomId);
        event.put("playerId", extractString(node, "drawerId", connectionManager.getPlayerId(sessionId)));
        event.put("points", node.get("points"));
        controlBroadcast(roomId, sessionId, "DRAW_BATCH_EVENT", toJson(event));
        return Mono.empty();
    }

    private Mono<String> handleClearCanvas(String sessionId, JsonNode json) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        if (roomId == null || roomId.isBlank()) {
            return Mono.empty();
        }
        // TV6 (D7b fix): CLEAR_CANVAS over JSON must be authorized the same way as the
        // binary fast path — only the current drawer may clear the canvas.
        if (!isAuthorizedDrawer(sessionId, roomId)) {
            log.info("CLEAR_CANVAS (JSON) rejected — session={} is not the current drawer in room={}", sessionId, roomId);
            return Mono.empty();
        }

        controlBroadcast(roomId, sessionId, "CANVAS_CLEARED",
                createBroadcastJson("CANVAS_CLEARED", roomId,
                        extractString(node, "drawerId", connectionManager.getPlayerId(sessionId)), ""));
        return Mono.empty();
    }

    /**
     * TV6: authorization for the JSON drawing fallback path, mirroring
     * {@code DrawingAuthorizationService} checks for the binary fast path:
     * the session's bound player must be the current drawer of an active round.
     */
    private boolean isAuthorizedDrawer(String sessionId, String roomId) {
        String playerId = connectionManager.getPlayerId(sessionId);
        if (playerId == null || playerId.isBlank()) {
            return false;
        }
        return drawingRoomStateCache.get(roomId)
                .map(state -> state.isPlaying() && playerId.equals(state.currentDrawerId()))
                .orElse(false);
    }

    private JsonNode getPayloadOrRoot(JsonNode json) {
        return json.has("payload") ? json.get("payload") : json;
    }

    /**
     * TV6: room-scoped control broadcast — local fanout + Redis Pub/Sub for cross-Gateway
     * delivery. Falls back to local-only when the router is absent (legacy unit tests).
     */
    private void controlBroadcast(String roomId, String senderSessionId, String eventType, String payloadJson) {
        if (controlEventRouter != null) {
            if (senderSessionId != null) {
                controlEventRouter.broadcastToRoomExcept(roomId, eventType, payloadJson, senderSessionId);
            } else {
                controlEventRouter.broadcastToRoom(roomId, eventType, payloadJson);
            }
        } else {
            if (senderSessionId != null) {
                connectionManager.broadcastToRoomExcept(roomId, senderSessionId, payloadJson);
            } else {
                connectionManager.broadcastToRoom(roomId, payloadJson);
            }
        }
    }

    public void broadcastDisconnect(String roomId, String sessionId, String playerId, String username) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "PLAYER_DISCONNECTED");
        payload.put("roomId", roomId);
        payload.put("playerId", playerId);
        payload.put("username", username != null && !username.isBlank() ? username : "Người chơi");
        controlBroadcast(roomId, sessionId, "PLAYER_DISCONNECTED", toJson(payload));
    }

    private String extractRequestId(JsonNode json) {
        if (json.has("requestId") && !json.get("requestId").isNull()) {
            return json.get("requestId").asText();
        }
        return null;
    }

    private String extractString(JsonNode node, String fieldName, String defaultValue) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            String val = node.get(fieldName).asText();
            if (!val.isBlank()) return val;
        }
        return defaultValue;
    }

    private Map<String, Object> createGuessResultMap(String roomId, String playerId, String status, int scoreAwarded, String requestId) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "GUESS_RESULT");
        if (requestId != null && !requestId.isBlank()) {
            map.put("requestId", requestId);
        }
        map.put("roomId", roomId);
        map.put("playerId", playerId);
        map.put("status", status);
        map.put("isCorrect", "CORRECT".equalsIgnoreCase(status));
        map.put("scoreAwarded", scoreAwarded);
        return map;
    }

    private String createChatMessageBroadcastJson(ChatMessageResponse chatRes, String requestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", chatRes.getMessageId());
        payload.put("roomId", chatRes.getRoomId());
        payload.put("playerId", chatRes.getPlayerId());
        payload.put("username", chatRes.getUsername());
        payload.put("content", chatRes.getContent());
        payload.put("type", chatRes.getType());
        payload.put("createdAt", chatRes.getCreatedAtEpochMs());

        Map<String, Object> map = new HashMap<>();
        map.put("type", "CHAT_MESSAGE");
        if (requestId != null && !requestId.isBlank()) {
            map.put("requestId", requestId);
        }
        map.put("payload", payload);
        return toJson(map);
    }

    private String createRoomSuccessJson(String type, RoomResponse room, String requestId) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        if (requestId != null && !requestId.isBlank()) {
            map.put("requestId", requestId);
        }
        map.put("roomId", room.getRoomId());
        map.put("name", room.getName());
        map.put("status", room.getStatus());
        map.put("hostPlayerId", room.getHostId());
        map.put("maxPlayers", room.getMaxPlayers());
        map.put("roundCount", room.getRoundCount());
        map.put("roundDuration", room.getRoundDuration());
        map.put("playerCount", room.getPlayersCount());

        List<Map<String, Object>> players = new ArrayList<>();
        for (PlayerMessage p : room.getPlayersList()) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("playerId", p.getPlayerId());
            pm.put("username", p.getUsername());
            pm.put("ready", p.getReady()); // TV10: authoritative lobby readiness
            players.add(pm);
        }
        map.put("players", players);

        return toJson(map);
    }

    private String createGameStateJson(String type, GameStateResponse state, String requestId) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        if (requestId != null && !requestId.isBlank()) {
            map.put("requestId", requestId);
        }
        map.put("roomId", state.getRoomId());
        map.put("status", state.getStatus());
        map.put("currentRound", state.getCurrentRound());
        map.put("totalRounds", state.getTotalRounds());
        map.put("drawerId", state.getDrawerId());
        map.put("roundStartedAt", state.getRoundStartedAt());
        map.put("roundEndsAt", state.getRoundEndsAt());
        map.put("hint", state.getHint());
        if (state.getSecretWord() != null && !state.getSecretWord().isEmpty()) {
            map.put("secretWord", state.getSecretWord());
        }

        List<Map<String, Object>> scores = new ArrayList<>();
        for (PlayerScoreMessage p : state.getScoresList()) {
            Map<String, Object> sm = new HashMap<>();
            sm.put("playerId", p.getPlayerId());
            sm.put("username", p.getUsername());
            sm.put("score", p.getScore());
            sm.put("hasGuessed", p.getHasGuessed());
            scores.add(sm);
        }
        map.put("scores", scores);

        return toJson(map);
    }

    private String createBroadcastJson(String type, String roomId, String playerId, String username) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("roomId", roomId);
        map.put("playerId", playerId);
        map.put("username", username);
        return toJson(map);
    }

    private String createGuessCorrectBroadcastJson(String roomId, String playerId, int scoreAwarded) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "PLAYER_GUESSED_CORRECTLY");
        map.put("roomId", roomId);
        map.put("playerId", playerId);
        map.put("scoreAwarded", scoreAwarded);
        return toJson(map);
    }

    /**
     * TV8: strip internal details from exception messages before sending to the
     * browser — keep a short safe line, drop everything after the first line and
     * remove obvious internal signatures (class names, "because the return value…").
     */
    private static String sanitizeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Request failed";
        }
        String firstLine = message.split("\n", 2)[0].trim();
        if (firstLine.isEmpty()) return "Request failed";
        // Internal exception signatures (NPE/class references) → generic safe text
        if (firstLine.contains("Cannot invoke") || firstLine.contains("Exception")
                || firstLine.contains("java.") || firstLine.length() > 200) {
            return "Request failed due to an internal error";
        }
        return firstLine;
    }

    private String createErrorJson(String requestId, String errorCode, String message) {
        // TV8 error sanitization: internal details (stack traces, NPE messages,
        // class names, gRPC internals) must never reach the browser. Only the
        // stable code plus a safe one-line message is exposed.
        Map<String, Object> map = new HashMap<>();
        map.put("type", "ERROR");
        if (requestId != null && !requestId.isBlank()) {
            map.put("requestId", requestId);
        }
        map.put("code", errorCode);
        map.put("message", sanitizeErrorMessage(message));
        Map<String, Object> errObj = new HashMap<>();
        errObj.put("code", errorCode);
        errObj.put("message", sanitizeErrorMessage(message));
        map.put("error", errObj);
        return toJson(map);
    }

    private String mapGrpcErrorCode(Throwable e) {
        if (e instanceof StatusRuntimeException sre) {
            Status.Code code = sre.getStatus().getCode();
            return switch (code) {
                case NOT_FOUND -> "CHAT_ROOM_NOT_FOUND";
                case PERMISSION_DENIED -> "CHAT_NOT_ALLOWED";
                case INVALID_ARGUMENT -> "INVALID_CHAT_MESSAGE";
                case RESOURCE_EXHAUSTED -> "CHAT_RATE_LIMITED";
                case UNAVAILABLE -> "CHAT_SERVICE_UNAVAILABLE";
                case DEADLINE_EXCEEDED -> "CHAT_SERVICE_TIMEOUT";
                default -> "CHAT_ERROR";
            };
        }
        return "INTERNAL_ERROR";
    }

    private String mapResumeErrorCode(Throwable e) {
        if (e instanceof StatusRuntimeException sre) {
            return switch (sre.getStatus().getCode()) {
                case NOT_FOUND -> "ROOM_NOT_FOUND";
                case UNAVAILABLE, DEADLINE_EXCEEDED -> "RESUME_RETRYABLE";
                default -> "INVALID_SESSION";
            };
        }
        return "RESUME_RETRYABLE";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"type\":\"ERROR\",\"message\":\"JSON serialization error\"}";
        }
    }

    /**
     * TV3 — update the drawing fast-path cache from a Game Service response.
     * Called on GAME_STARTED and GET_GAME_STATE (when PLAYING) to keep the cache consistent.
     *
     * <p>TODO (phase 2): also handle ROUND_STARTED / ROUND_ENDED events pushed by Game Service
     * to keep cache in sync across round transitions without requiring GET_GAME_STATE per round.
     */
    private void updateDrawingCache(String roomId, GameStateResponse gameState) {
        if (gameState.getDrawerId() != null && !gameState.getDrawerId().isBlank()) {
            DrawingRoomState state = DrawingRoomState.playing(
                    gameState.getDrawerId(),
                    gameState.getCurrentRound()
            );
            drawingRoomStateCache.update(roomId, state);
            log.debug("DrawingRoomStateCache updated via game event: room={} drawer={} round={}",
                    roomId, gameState.getDrawerId(), gameState.getCurrentRound());
        }
    }

    /**
     * TV3 — handle GAME_FINISHED event.
     *
     * <p>Clears the drawing fast-path cache for the room so stale state doesn't
     * accumulate for next game. Also broadcasts the event to all players in the room.
     *
     * <p>This can be triggered by a client signal or by Game Service push (phase 2).
     * For phase 1, the client sends GAME_FINISHED when it receives a game-over event from Game Service.
     */
    private Mono<String> handleGameFinished(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));

        if (roomId == null || roomId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "GAME_FINISHED_FAILED", "roomId is required"));
        }

        // TV3: evict drawing cache — game is over, state is stale
        drawingRoomStateCache.remove(roomId);
        log.info("DrawingRoomStateCache evicted on GAME_FINISHED: room={}", roomId);

        // Broadcast GAME_FINISHED to all players in room — local + Redis fanout (TV6)
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "GAME_FINISHED");
        payload.put("roomId", roomId);
        if (requestId != null) payload.put("requestId", requestId);
        controlBroadcast(roomId, null, "GAME_FINISHED", toJson(payload));

        Map<String, Object> response = new HashMap<>();
        response.put("type", "GAME_FINISHED_ACK");
        if (requestId != null) response.put("requestId", requestId);
        return Mono.just(toJson(response));
    }
}

