package com.drawgame.realtime_gateway.websocket.handler;

import com.drawgame.chat.grpc.generated.ChatMessageResponse;
import com.drawgame.game.grpc.generated.GameStateResponse;
import com.drawgame.game.grpc.generated.PlayerScoreMessage;
import com.drawgame.game.grpc.generated.WordChoiceMessage;
import com.drawgame.game.grpc.generated.RoundRecapMessage;
import com.drawgame.realtime_gateway.chat.LobbyChatRepository;
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
    private final LobbyChatRepository lobbyChatRepository;
    private final ObjectMapper objectMapper;
    private final String gatewayInstanceId;
    /**
     * TV8 security: signed game-session credentials, per-session rate limits, input
     * bounds.
     */
    private final GameSessionTokenService tokenService;
    private final SessionRateLimiter rateLimiter;
    private final InputValidator inputValidator;

    public GameCommandHandler(
            GameGrpcClient gameGrpcClient,
            RoomGrpcClient roomGrpcClient,
            ChatGrpcClient chatGrpcClient,
            ConnectionManager connectionManager,
            DrawingRoomStateCache drawingRoomStateCache) {
        this(gameGrpcClient, roomGrpcClient, chatGrpcClient, connectionManager, drawingRoomStateCache,
                null, null, null, null, null, null, "gateway-default");
    }

    public GameCommandHandler(
            GameGrpcClient gameGrpcClient,
            RoomGrpcClient roomGrpcClient,
            ChatGrpcClient chatGrpcClient,
            ConnectionManager connectionManager,
            DrawingRoomStateCache drawingRoomStateCache,
            @org.springframework.beans.factory.annotation.Value("${gateway.instance-id:gateway-1}") String gatewayInstanceId) {
        this(gameGrpcClient, roomGrpcClient, chatGrpcClient, connectionManager, drawingRoomStateCache,
                null, null, null, null, null, null, gatewayInstanceId);
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
            LobbyChatRepository lobbyChatRepository,
            GameSessionTokenService tokenService,
            SessionRateLimiter rateLimiter,
            InputValidator inputValidator,
            @org.springframework.beans.factory.annotation.Value("${gateway.instance-id:gateway-1}") String gatewayInstanceId) {
        this.gameGrpcClient = gameGrpcClient;
        this.roomGrpcClient = roomGrpcClient;
        this.chatGrpcClient = chatGrpcClient;
        this.connectionManager = connectionManager;
        this.drawingRoomStateCache = drawingRoomStateCache;
        this.controlEventRouter = controlEventRouter;
        this.recoveryRepository = recoveryRepository;
        this.lobbyChatRepository = lobbyChatRepository;
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
        // tiny payload). Drawing binary frames are limited in the binary transport
        // path.
        if (rateLimiter != null && !"PING".equalsIgnoreCase(type) && !"APP_PING".equalsIgnoreCase(type)) {
            SessionRateLimiter.Bucket bucket = switch (type) {
                case "SUBMIT_GUESS", "SEND_CHAT", "SEND_LOBBY_CHAT" -> SessionRateLimiter.Bucket.GUESS;
                case "SEND_REACTION" -> SessionRateLimiter.Bucket.REACTION;
                case "DRAW_POINT", "DRAW_BATCH", "CLEAR_CANVAS" -> SessionRateLimiter.Bucket.DRAW;
                case "VOICE_SIGNAL", "VOICE_STATE_UPDATE" -> SessionRateLimiter.Bucket.VOICE;
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
            case "LIST_ROOMS" -> handleListRooms(json, requestId);
            case "LEAVE_ROOM" -> handleLeaveRoom(sessionId, json, requestId);
            case "START_GAME" -> handleStartGame(sessionId, json, requestId);
            case "GET_GAME_STATE" -> handleGetGameState(sessionId, json, requestId);
            case "SELECT_WORD" -> handleSelectWord(sessionId, json, requestId);
            case "SUBMIT_GUESS" -> handleSubmitGuess(sessionId, json, requestId);
            case "GET_CANVAS_STATE" -> handleGetCanvasState(sessionId, json, requestId);
            // TV10: lobby/product features — all derive identity from the bound session
            case "SET_READY" -> handleSetReady(sessionId, json, requestId);
            case "SET_CATEGORIES" -> handleSetCategories(sessionId, json, requestId);
            case "REMATCH" -> handleRematch(sessionId, json, requestId);
            case "KICK_PLAYER" -> handleKickPlayer(sessionId, json, requestId);
            case "SEND_CHAT" -> handleSendChat(sessionId, json, requestId);
            case "SEND_LOBBY_CHAT" -> handleSendLobbyChat(sessionId, json, requestId);
            case "GET_LOBBY_CHAT" -> handleGetLobbyChat(sessionId, json, requestId);
            case "SEND_REACTION" -> handleSendReaction(sessionId, json, requestId);
            case "GET_RECENT_CHAT" -> handleGetRecentChat(sessionId, json, requestId);
            case "DRAW_POINT" -> handleDrawPoint(sessionId, json);
            case "DRAW_BATCH" -> handleDrawBatch(sessionId, json);
            case "CLEAR_CANVAS" -> handleClearCanvas(sessionId, json);
            // TV3: clear drawing cache when game finishes
            case "GAME_FINISHED" -> handleGameFinished(sessionId, json, requestId);
            // TV12: WebRTC Voice Chat
            case "SET_VOICE_CHAT_ENABLED" -> handleSetVoiceChatEnabled(sessionId, json, requestId);
            case "VOICE_SIGNAL" -> handleVoiceSignal(sessionId, json, requestId);
            case "VOICE_STATE_UPDATE" -> handleVoiceStateUpdate(sessionId, json, requestId);
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
        int maxPlayers = node.has("maxPlayers") ? node.get("maxPlayers").asInt()
                : (node.has("max_players") ? node.get("max_players").asInt() : 4);
        int totalRounds = node.has("totalRounds") ? node.get("totalRounds").asInt()
                : (node.has("roundCount") ? node.get("roundCount").asInt() : 5);
        int roundDuration = node.has("roundDuration") ? node.get("roundDuration").asInt()
                : (node.has("drawTime") ? node.get("drawTime").asInt() : 60);
        // Keep this bound aligned with RoomManagementService (2..10 players).
        // Clamping here also protects older clients that still submit 11 or 12.
        maxPlayers = Math.max(2, Math.min(10, maxPlayers));
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

    /**
     * Null-safe sanitizer helper — falls back to the default when input is invalid.
     */
    private String sanitizeOr(InputValidator validator, String raw, String fallback) {
        if (validator == null) {
            return (raw == null || raw.isBlank()) ? fallback : raw;
        }
        if (raw == null || raw.isBlank())
            return fallback;
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
                return Mono.just(createErrorJson(requestId, "INVALID_ROOM_CODE",
                        "Room code must be 4-32 uppercase letters/digits"));
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
                    // TV6: room-scoped control event — local broadcast + Redis fanout to other
                    // Gateways
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
                                "Player is not a member of room: " + roomId);
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
                        e.getMessage() != null ? e.getMessage() : "Unable to resume session")));
    }

    /**
     * TV8: attach a freshly issued signed game-session token to a CREATE/JOIN
     * response.
     * The token is the resume credential — claims sub=playerId, room=roomId,
     * purpose=GAME_SESSION.
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
     * Host identity is taken only from the authenticated, room-bound WebSocket
     * session.
     */
    private Mono<String> handleSetCategories(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }
        JsonNode categoryNodes = node.get("selectedCategories");
        if (categoryNodes == null || !categoryNodes.isArray()) {
            return Mono.just(createErrorJson(requestId, "INVALID_CATEGORIES", "selectedCategories must be an array"));
        }
        List<String> categories = new ArrayList<>();
        for (JsonNode category : categoryNodes) {
            if (!category.isTextual()) {
                return Mono
                        .just(createErrorJson(requestId, "INVALID_CATEGORIES", "Category identifiers must be strings"));
            }
            categories.add(category.asText());
        }

        return roomGrpcClient.setCategories(roomId, playerId, categories)
                .map(room -> {
                    controlBroadcast(roomId, null, "ROOM_CATEGORIES_UPDATED",
                            createRoomSuccessJson("ROOM_CATEGORIES_UPDATED", room, null));
                    return createRoomSuccessJson("ROOM_INFO", room, requestId);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "SET_CATEGORIES_FAILED", e.getMessage())));
    }

    /**
     * TV12: Host-only room-level voice chat setting. Allowed in both WAITING and PLAYING states.
     * Identity derived authoritatively from WebSocket session.
     */
    private Mono<String> handleSetVoiceChatEnabled(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }
        boolean enabled = false;
        if (node.has("voiceChatEnabled")) {
            enabled = node.get("voiceChatEnabled").asBoolean();
        } else if (node.has("enabled")) {
            enabled = node.get("enabled").asBoolean();
        }

        return roomGrpcClient.setVoiceChatEnabled(roomId, playerId, enabled)
                .map(room -> {
                    Map<String, Object> event = new HashMap<>();
                    event.put("type", "VOICE_CHAT_SETTING_CHANGED");
                    event.put("roomId", roomId);
                    event.put("voiceChatEnabled", room.getVoiceChatEnabled());
                    event.put("updatedBy", playerId);
                    String eventJson = toJson(event);
                    controlBroadcast(roomId, null, "VOICE_CHAT_SETTING_CHANGED", eventJson);

                    Map<String, Object> resp = new HashMap<>();
                    resp.put("type", "VOICE_CHAT_SETTING_CHANGED");
                    if (requestId != null && !requestId.isBlank()) {
                        resp.put("requestId", requestId);
                    }
                    resp.put("roomId", roomId);
                    resp.put("voiceChatEnabled", room.getVoiceChatEnabled());
                    return toJson(resp);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "SET_VOICE_CHAT_FAILED", e.getMessage())));
    }

    /**
     * TV12: Targeted peer-to-peer WebRTC signaling (SDP offer/answer, ICE candidates).
     * The senderPlayerId is strictly and authoritatively derived from the WebSocket session.
     */
    private Mono<String> handleVoiceSignal(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = connectionManager.getRoomId(sessionId);
        final String senderPlayerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || senderPlayerId == null || senderPlayerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }
        String targetPlayerId = node.hasNonNull("targetPlayerId") ? node.get("targetPlayerId").asText().trim() : null;
        if (targetPlayerId == null || targetPlayerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_TARGET", "targetPlayerId is required"));
        }
        if (targetPlayerId.equals(senderPlayerId)) {
            return Mono.empty();
        }

        Map<String, Object> signalMsg = new HashMap<>();
        signalMsg.put("type", "VOICE_SIGNAL");
        signalMsg.put("roomId", roomId);
        signalMsg.put("senderPlayerId", senderPlayerId); // Authoritative!
        signalMsg.put("targetPlayerId", targetPlayerId);
        if (node.has("signal")) {
            signalMsg.put("signal", node.get("signal"));
        }
        if (node.has("signalType")) {
            signalMsg.put("signalType", node.get("signalType").asText());
        }
        if (node.has("sdp")) {
            signalMsg.put("sdp", node.get("sdp"));
        }
        if (node.has("candidate")) {
            signalMsg.put("candidate", node.get("candidate"));
        }
        String signalJson = toJson(signalMsg);

        // 1. Deliver locally if target is connected to this Gateway instance
        String targetSessionId = connectionManager.getSessionForPlayer(roomId, targetPlayerId);
        if (targetSessionId != null) {
            connectionManager.sendToSession(targetSessionId, signalJson);
        }

        // 2. Publish to Redis so remote Gateways can deliver if target is connected there
        if (controlEventRouter != null) {
            controlEventRouter.publishToRedisOnly(roomId, "VOICE_SIGNAL", signalJson);
        }

        return Mono.empty();
    }

    /**
     * TV12: Room-wide voice activity/state update (speaking indicator, mute/deafen toggle).
     * Broadcasts to all peers in the room.
     */
    private Mono<String> handleVoiceStateUpdate(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }

        Map<String, Object> event = new HashMap<>();
        event.put("type", "VOICE_STATE_UPDATE");
        event.put("roomId", roomId);
        event.put("playerId", playerId);
        if (node.has("isMuted")) {
            event.put("isMuted", node.get("isMuted").asBoolean());
        }
        if (node.has("isDeafened")) {
            event.put("isDeafened", node.get("isDeafened").asBoolean());
        }
        if (node.has("isSpeaking")) {
            event.put("isSpeaking", node.get("isSpeaking").asBoolean());
        }

        String eventJson = toJson(event);
        controlBroadcast(roomId, sessionId, "VOICE_STATE_UPDATE", eventJson);
        return Mono.empty();
    }


    /**
     * TV10 REMATCH: FINISHED -> WAITING. Room/members/config preserved;
     * ready state cleared. Old match result already persisted by Game Service.
     * Also defensively clears drawing auth cache + canvas recovery state on every
     * Gateway (cross-Gateway control event does the same on remote gateways).
     */
    private Mono<String> handleRematch(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        final String playerId = extractString(node, "playerId", connectionManager.getPlayerId(sessionId));
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
                            .map(p -> Map.of("playerId", p.getPlayerId(), "username", p.getUsername(), "ready",
                                    p.getReady()))
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

    private Mono<String> handleListRooms(JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        int limit = node.has("limit") ? Math.max(1, Math.min(node.get("limit").asInt(10), 50)) : 10;

        return roomGrpcClient.listRooms(limit)
                .map(response -> {
                    List<Map<String, Object>> rooms = response.getRoomsList().stream()
                            .map(room -> {
                                Map<String, Object> item = new HashMap<>();
                                item.put("roomId", room.getRoomId());
                                item.put("name", room.getName());
                                item.put("status", room.getStatus());
                                item.put("playerCount", room.getPlayersCount());
                                item.put("maxPlayers", room.getMaxPlayers());
                                return item;
                            })
                            .toList();
                    Map<String, Object> result = new HashMap<>();
                    result.put("type", "ROOM_LIST");
                    if (requestId != null && !requestId.isBlank()) {
                        result.put("requestId", requestId);
                    }
                    result.put("rooms", rooms);
                    return toJson(result);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(
                        requestId, "LIST_ROOMS_FAILED", e.getMessage())));
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

        return removePlayerFromRoomAndGame(roomId, playerId)
                .map(response -> {
                    publishPlayerLeft(response, roomId, sessionId, playerId, resolvedLeavingUsername);
                    // Explicit leave keeps the socket open, so remove its room authorization.
                    connectionManager.unbindSession(sessionId);
                    return createRoomSuccessJson("ROOM_LEFT", response, requestId);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "LEAVE_ROOM_FAILED", e.getMessage())));
    }

    /**
     * A closed WebSocket is a permanent leave. Removing Room membership makes any
     * later RESUME fail instead of adding the disconnected player back to the
     * roster.
     */
    public Mono<Void> handleDisconnect(String roomId, String sessionId, String playerId, String username) {
        String resolvedUsername = username != null && !username.isBlank() ? username : "Người chơi";
        return removePlayerFromRoomAndGame(roomId, playerId)
                .doOnNext(response -> publishPlayerLeft(
                        response, roomId, sessionId, playerId, resolvedUsername))
                .doOnSuccess(ignored -> log.info(
                        "Disconnected player permanently removed: room={} player={}", roomId, playerId))
                .onErrorResume(e -> {
                    log.error("Could not permanently remove disconnected player: room={} player={}",
                            roomId, playerId, e);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<RoomResponse> removePlayerFromRoomAndGame(String roomId, String playerId) {
        return roomGrpcClient.leaveRoom(roomId, playerId)
                .flatMap(response -> gameGrpcClient.removePlayer(roomId, playerId)
                        .doOnNext(gameState -> {
                            if ("PLAYING".equalsIgnoreCase(gameState.getStatus())
                                    && !gameState.getDrawerId().isBlank()) {
                                updateDrawingCache(roomId, gameState);
                            } else if ("FINISHED".equalsIgnoreCase(gameState.getStatus())) {
                                drawingRoomStateCache.remove(roomId);
                            }
                        })
                        .onErrorResume(e -> {
                            // Room membership is authoritative for resume. Keep the leave successful
                            // even if Game Service is temporarily unavailable.
                            log.error("Could not remove leaving player from game state: room={} player={}",
                                    roomId, playerId, e);
                            return Mono.empty();
                        })
                        .thenReturn(response));
    }

    private void publishPlayerLeft(
            RoomResponse response,
            String roomId,
            String sessionId,
            String playerId,
            String username) {
        Map<String, Object> leftPayload = new HashMap<>();
        leftPayload.put("type", "PLAYER_LEFT");
        leftPayload.put("roomId", roomId);
        leftPayload.put("playerId", playerId);
        leftPayload.put("username", username);
        leftPayload.put("hostPlayerId", response.getHostId());
        leftPayload.put("players", response.getPlayersList().stream()
                .map(p -> Map.of(
                        "playerId", p.getPlayerId(),
                        "username", p.getUsername(),
                        "ready", p.getReady()))
                .collect(java.util.stream.Collectors.toList()));
        controlBroadcast(roomId, sessionId, "PLAYER_LEFT", toJson(leftPayload));

        if (response.getPlayersList().isEmpty()) {
            drawingRoomStateCache.remove(roomId);
            log.info("DrawingRoomStateCache evicted - last player left room={}", roomId);
        }
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
                    return stateJson;
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "START_GAME_FAILED", e.getMessage())));
    }

    private Mono<String> handleGetGameState(String sessionId, JsonNode json, String requestId) {
        // TV8: bound room + AUTHENTICATED session identity — viewer identity cannot be
        // spoofed
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }

        return gameGrpcClient.getGameState(roomId, playerId)
                .map(gameState -> {
                    // TV3: sync drawing fast-path cache on GET_GAME_STATE (handles cache miss after
                    // restart)
                    if ("PLAYING".equalsIgnoreCase(gameState.getStatus())
                            && "DRAWING".equals(gameState.getRoundPhase())) {
                        updateDrawingCache(roomId, gameState);
                    } else {
                        drawingRoomStateCache.remove(roomId);
                    }
                    return createGameStateJson("GAME_STATE", gameState, requestId);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "GET_GAME_STATE_FAILED", e.getMessage())));
    }

    /**
     * Private drawer command; player identity comes exclusively from the bound
     * session.
     */
    private Mono<String> handleSelectWord(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = connectionManager.getRoomId(sessionId);
        String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }
        String choiceId = extractString(node, "choiceId", "");
        if (choiceId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_WORD_CHOICE", "choiceId is required"));
        }
        return gameGrpcClient.selectWord(roomId, playerId, choiceId)
                .map(state -> createGameStateJson("GAME_STATE", state, requestId))
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "SELECT_WORD_FAILED", e.getMessage())));
    }

    /**
     * TV7 — current-round Canvas recovery (doc/reconnect-canvas-recovery.md §6).
     *
     * <p>
     * Validates: session bound to a room+player (via RESUME_SESSION/JOIN), an
     * active
     * PLAYING game, and the requested round matching the authoritative current
     * round.
     * Reads the shared Redis Stream so ANY Gateway can serve recovery. Responds
     * with
     * SYNC_CANVAS_STATE carrying drawing events ONLY (never secretWord/game
     * internals).
     */
    private Mono<String> handleGetCanvasState(String sessionId, JsonNode json, String requestId) {
        if (recoveryRepository == null) {
            // Legacy unit-test constructor — recovery not wired
            return Mono.just(createErrorJson(requestId, "RECOVERY_NOT_AVAILABLE", "Canvas recovery not available"));
        }

        JsonNode node = getPayloadOrRoot(json);
        // Identity comes from the BOUND session context — client-supplied ids are
        // ignored
        final String roomId = connectionManager.getRoomId(sessionId);
        final String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room/player"));
        }

        final int requestedRound = node.has("round") ? node.get("round").asInt(-1) : -1;
        if (requestedRound < 0) {
            return Mono.just(createErrorJson(requestId, "INVALID_RECOVERY_REQUEST", "round is required"));
        }

        // Authoritative round check via Game Service (cache may be stale on this
        // Gateway)
        return gameGrpcClient.getGameState(roomId, playerId)
                .flatMap(gameState -> {
                    if (!"PLAYING".equalsIgnoreCase(gameState.getStatus())) {
                        return Mono.just(createErrorJson(requestId, "GAME_NOT_ACTIVE",
                                "Game is not active: " + gameState.getStatus()));
                    }
                    if (!"DRAWING".equals(gameState.getRoundPhase())) {
                        return Mono.just(createErrorJson(requestId, "DRAWING_NOT_ACTIVE",
                                "Canvas recovery is available during drawing only"));
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

    /**
     * SYNC_CANVAS_STATE response — drawing events only, never secret word or game
     * internals.
     */
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
        // Use the nickname bound during CREATE/JOIN/RESUME. The nickname in the
        // request payload is intentionally ignored because it can be spoofed.
        final String username = connectionManager.getUsername(sessionId);
        String guess = extractString(node, "guess", extractString(node, "content", ""));

        // TV8: guess input bounds (Vietnamese Unicode preserved; Game Service matching
        // unchanged)
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
                        // Payload intentionally contains no answer text. It does include the
                        // authoritative bound nickname so clients never have to display playerId.
                        String broadcastMsg = createGuessCorrectBroadcastJson(
                                roomId, playerId, username, response.getScoreAwarded());
                        controlBroadcast(roomId, sessionId, "PLAYER_GUESSED_CORRECTLY", broadcastMsg);

                        Map<String, Object> map = createGuessResultMap(roomId, playerId, status,
                                response.getScoreAwarded(), requestId);

                        // TV3 Stabilization (GW-05/GW-06): a correct guess may trigger round
                        // transition.
                        // Refresh drawing cache so new drawer/round is authoritative without delay.
                        // Failure to refresh is non-fatal — next GET_GAME_STATE will re-sync.
                        return gameGrpcClient.getGameState(roomId, playerId)
                                .doOnNext(gameState -> {
                                    if ("PLAYING".equalsIgnoreCase(gameState.getStatus())) {
                                        updateDrawingCache(roomId, gameState);
                                        log.info(
                                                "DrawingRoomStateCache refreshed after CORRECT guess: room={} drawer={} round={}",
                                                roomId, gameState.getDrawerId(), gameState.getCurrentRound());
                                    } else {
                                        // Game finished after last round
                                        drawingRoomStateCache.remove(roomId);
                                        log.info(
                                                "DrawingRoomStateCache evicted — game ended after CORRECT guess: room={} status={}",
                                                roomId, gameState.getStatus());
                                    }
                                })
                                .onErrorResume(e -> {
                                    log.warn(
                                            "Failed to refresh drawing cache after CORRECT guess: room={} — will resync on next GET_GAME_STATE: {}",
                                            roomId, e.getMessage());
                                    return reactor.core.publisher.Mono.empty();
                                })
                                .thenReturn(toJson(map));
                    } else {
                        // BUG-1 fix: Guess input and Chat must remain completely separate.
                        // Non-CORRECT guesses (WRONG, CLOSE, etc.) are private feedback to the
                        // submitter
                        // and MUST NOT be forwarded to Chat Service or broadcast as CHAT_MESSAGE.
                        Map<String, Object> map = createGuessResultMap(roomId, playerId, status,
                                response.getScoreAwarded(), requestId);
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
        // TV8: chat length is bounded server-side; Chat Service re-validates +
        // rate-limits

        return chatGrpcClient.sendMessage(roomId, playerId, "", content)
                .map(chatRes -> {
                    String chatBroadcastJson = createChatMessageBroadcastJson(chatRes, null);
                    // TV6: CHAT_MESSAGE is room-scoped — local broadcast + Redis fanout
                    controlBroadcast(roomId, null, "CHAT_MESSAGE", chatBroadcastJson);
                    return createChatMessageBroadcastJson(chatRes, requestId);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, mapGrpcErrorCode(e), e.getMessage())));
    }

    private Mono<String> handleSendLobbyChat(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        String playerId = extractString(node, "playerId", connectionManager.getPlayerId(sessionId));
        if (playerId == null || playerId.isBlank()) {
            playerId = "guest_" + sessionId.substring(0, Math.min(6, sessionId.length()));
        }

        String rawUsername = extractString(node, "username", connectionManager.getUsername(sessionId));
        String username = sanitizeOr(inputValidator, rawUsername, "Người chơi");

        String content = extractString(node, "content", "").trim();
        if (content.isEmpty()) {
            return Mono.just(createErrorJson(requestId, "INVALID_CHAT_MESSAGE", "Chat content cannot be empty"));
        }
        if (content.length() > 300) {
            content = content.substring(0, 300);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", java.util.UUID.randomUUID().toString());
        payload.put("roomId", "lobby");
        payload.put("playerId", playerId);
        payload.put("username", username);
        payload.put("content", content);
        payload.put("type", "USER");
        payload.put("createdAt", System.currentTimeMillis());

        Map<String, Object> event = new HashMap<>();
        event.put("type", "LOBBY_CHAT_MESSAGE");
        event.put("payload", payload);
        String broadcastJson = toJson(event);

        // Fanout to all lobby users across gateways
        controlBroadcast("lobby", null, "LOBBY_CHAT_MESSAGE", broadcastJson);

        // Persist message asynchronously
        if (lobbyChatRepository != null) {
            lobbyChatRepository.appendMessage(payload).subscribe();
        }

        Map<String, Object> ack = new HashMap<>(event);
        if (requestId != null && !requestId.isBlank()) {
            ack.put("requestId", requestId);
        }
        return Mono.just(toJson(ack));
    }

    private Mono<String> handleGetLobbyChat(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        int limit = Math.max(1, Math.min(50, node.has("limit") ? node.get("limit").asInt() : 30));

        if (lobbyChatRepository == null) {
            Map<String, Object> emptyMap = new HashMap<>();
            emptyMap.put("type", "LOBBY_CHAT_HISTORY");
            if (requestId != null && !requestId.isBlank()) {
                emptyMap.put("requestId", requestId);
            }
            emptyMap.put("messages", new ArrayList<>());
            return Mono.just(toJson(emptyMap));
        }

        return lobbyChatRepository.getRecentMessages(limit)
                .collectList()
                .map(messages -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "LOBBY_CHAT_HISTORY");
                    if (requestId != null && !requestId.isBlank()) {
                        map.put("requestId", requestId);
                    }
                    map.put("messages", messages);
                    return toJson(map);
                })
                .onErrorResume(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "LOBBY_CHAT_HISTORY");
                    if (requestId != null && !requestId.isBlank()) {
                        map.put("requestId", requestId);
                    }
                    map.put("messages", new ArrayList<>());
                    return Mono.just(toJson(map));
                });
    }

    private static final java.util.Set<String> ALLOWED_REACTIONS = java.util.Set.of("😂", "👍", "🔥", "😮", "🤔", "❤️");

    /**
     * Ephemeral, room-scoped reaction: never persisted to chat or drawing recovery.
     */
    private Mono<String> handleSendReaction(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = connectionManager.getRoomId(sessionId);
        String playerId = connectionManager.getPlayerId(sessionId);
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return Mono.just(createErrorJson(requestId, "INVALID_SESSION", "Session is not bound to a room"));
        }
        String reactionType = extractString(node, "reactionType", "");
        if (!ALLOWED_REACTIONS.contains(reactionType)) {
            return Mono.just(createErrorJson(requestId, "INVALID_REACTION", "Unsupported reaction"));
        }
        return gameGrpcClient.getGameState(roomId, playerId)
                .flatMap(state -> {
                    boolean member = state.getScoresList().stream()
                            .anyMatch(score -> playerId.equals(score.getPlayerId()));
                    if (!member) {
                        return Mono.just(createErrorJson(requestId, "ROOM_MEMBERSHIP_REQUIRED",
                                "Player is not an active match member"));
                    }
                    if (!"PLAYING".equalsIgnoreCase(state.getStatus()) || !"DRAWING".equals(state.getRoundPhase())) {
                        return Mono.just(createErrorJson(requestId, "REACTION_NOT_ALLOWED",
                                "Reactions are available during drawing only"));
                    }
                    Map<String, Object> event = new HashMap<>();
                    event.put("type", "REACTION");
                    event.put("roomId", roomId);
                    event.put("gameId", state.getGameId());
                    event.put("roundNumber", state.getCurrentRound());
                    event.put("playerId", playerId);
                    String displayName = connectionManager.getUsername(sessionId);
                    event.put("displayName", displayName == null || displayName.isBlank() ? playerId : displayName);
                    event.put("reactionType", reactionType);
                    String eventJson = toJson(event);
                    controlBroadcast(roomId, sessionId, "REACTION", eventJson);
                    event.put("requestId", requestId);
                    return Mono.just(toJson(event));
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "SEND_REACTION_FAILED", e.getMessage())));
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
        // TV6: JSON drawing fallback path — room-scoped control fanout (binary path
        // uses
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
            log.info("CLEAR_CANVAS (JSON) rejected — session={} is not the current drawer in room={}", sessionId,
                    roomId);
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
     * TV6: room-scoped control broadcast — local fanout + Redis Pub/Sub for
     * cross-Gateway
     * delivery. Falls back to local-only when the router is absent (legacy unit
     * tests).
     */
    private void controlBroadcast(String roomId, String senderSessionId, String eventType, String payloadJson) {
        if (controlEventRouter != null) {
            if (senderSessionId != null) {
                controlEventRouter.broadcastToRoomExcept(roomId, eventType, payloadJson, senderSessionId);
            } else {
                controlEventRouter.broadcastToRoom(roomId, eventType, payloadJson);
            }
        } else {
            if ("lobby".equalsIgnoreCase(roomId)) {
                if (senderSessionId != null) {
                    connectionManager.broadcastToLobbyExcept(senderSessionId, payloadJson);
                } else {
                    connectionManager.broadcastToLobby(payloadJson);
                }
            } else if (senderSessionId != null) {
                connectionManager.broadcastToRoomExcept(roomId, senderSessionId, payloadJson);
            } else {
                connectionManager.broadcastToRoom(roomId, payloadJson);
            }
        }
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
            if (!val.isBlank())
                return val;
        }
        return defaultValue;
    }

    private Map<String, Object> createGuessResultMap(String roomId, String playerId, String status, int scoreAwarded,
            String requestId) {
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
        map.put("selectedCategories", room.getSelectedCategoriesList());
        map.put("playerCount", room.getPlayersCount());
        map.put("voiceChatEnabled", room.getVoiceChatEnabled());

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
        map.put("gameId", state.getGameId());
        map.put("roundPhase", state.getRoundPhase());
        map.put("phaseStartedAt", state.getPhaseStartedAt());
        map.put("phaseEndsAt", state.getPhaseEndsAt());
        map.put("roundDurationSeconds", state.getRoundDurationSeconds());
        map.put("hint", state.getHint());
        if (state.getSecretWord() != null && !state.getSecretWord().isEmpty()) {
            map.put("secretWord", state.getSecretWord());
        }

        List<Map<String, Object>> wordChoices = new ArrayList<>();
        for (WordChoiceMessage choice : state.getWordChoicesList()) {
            wordChoices.add(Map.of("choiceId", choice.getChoiceId(), "displayWord", choice.getDisplayWord()));
        }
        map.put("wordChoices", wordChoices);
        if (state.hasRoundRecap()) {
            RoundRecapMessage recap = state.getRoundRecap();
            Map<String, Object> recapJson = new HashMap<>();
            recapJson.put("roundNumber", recap.getRoundNumber());
            recapJson.put("drawerId", recap.getDrawerId());
            recapJson.put("answer", recap.getAnswer());
            recapJson.put("scoreDeltas", recap.getScoreDeltasList().stream().map(delta -> Map.of(
                    "playerId", delta.getPlayerId(), "username", delta.getUsername(),
                    "roundDelta", delta.getRoundDelta(), "totalScore", delta.getTotalScore())).toList());
            recapJson.put("drawerScore", recap.getDrawerScore());
            recapJson.put("fastestPlayerId", recap.getFastestPlayerId());
            recapJson.put("fastestUsername", recap.getFastestUsername());
            recapJson.put("fastestElapsedMillis", recap.getFastestElapsedMillis());
            recapJson.put("correctPlayerIds", recap.getCorrectPlayerIdsList());
            recapJson.put("correctGuessTimes", recap.getCorrectGuessTimesList().stream().map(timing -> Map.of(
                    "playerId", timing.getPlayerId(), "elapsedMillis", timing.getElapsedMillis())).toList());
            map.put("roundRecap", recapJson);
        }
        map.put("awards", state.getAwardsList().stream().map(award -> Map.of(
                "type", award.getType(), "label", award.getLabel(), "playerId", award.getPlayerId(),
                "username", award.getUsername(), "value", award.getValue(),
                "elapsedMillis", award.getElapsedMillis())).toList());

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

    private String createGuessCorrectBroadcastJson(
            String roomId, String playerId, String username, int scoreAwarded) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "PLAYER_GUESSED_CORRECTLY");
        map.put("roomId", roomId);
        map.put("playerId", playerId);
        map.put("username", username);
        map.put("scoreAwarded", scoreAwarded);
        return toJson(map);
    }

    /**
     * TV8: strip internal details from exception messages before sending to the
     * browser — keep a short safe line, drop everything after the first line and
     * remove obvious internal signatures (class names, "because the return
     * value…").
     */
    private static String sanitizeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Request failed";
        }
        String firstLine = message.split("\n", 2)[0].trim();
        if (firstLine.isEmpty())
            return "Request failed";
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
     * Called on GAME_STARTED and GET_GAME_STATE (when PLAYING) to keep the cache
     * consistent.
     *
     * <p>
     * TODO (phase 2): also handle ROUND_STARTED / ROUND_ENDED events pushed by Game
     * Service
     * to keep cache in sync across round transitions without requiring
     * GET_GAME_STATE per round.
     */
    private void updateDrawingCache(String roomId, GameStateResponse gameState) {
        if ("DRAWING".equals(gameState.getRoundPhase())
                && gameState.getDrawerId() != null && !gameState.getDrawerId().isBlank()) {
            DrawingRoomState state = DrawingRoomState.playing(
                    gameState.getDrawerId(),
                    gameState.getCurrentRound());
            drawingRoomStateCache.update(roomId, state);
            log.debug("DrawingRoomStateCache updated via game event: room={} drawer={} round={}",
                    roomId, gameState.getDrawerId(), gameState.getCurrentRound());
        }
    }

    /**
     * TV3 — handle GAME_FINISHED event.
     *
     * <p>
     * Clears the drawing fast-path cache for the room so stale state doesn't
     * accumulate for next game. Also broadcasts the event to all players in the
     * room.
     *
     * <p>
     * This can be triggered by a client signal or by Game Service push (phase 2).
     * For phase 1, the client sends GAME_FINISHED when it receives a game-over
     * event from Game Service.
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
        if (requestId != null)
            payload.put("requestId", requestId);
        controlBroadcast(roomId, null, "GAME_FINISHED", toJson(payload));

        Map<String, Object> response = new HashMap<>();
        response.put("type", "GAME_FINISHED_ACK");
        if (requestId != null)
            response.put("requestId", requestId);
        return Mono.just(toJson(response));
    }
}
