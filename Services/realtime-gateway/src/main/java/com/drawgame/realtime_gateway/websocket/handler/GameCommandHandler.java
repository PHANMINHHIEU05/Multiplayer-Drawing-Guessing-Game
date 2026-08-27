package com.drawgame.realtime_gateway.websocket.handler;

import com.drawgame.chat.grpc.generated.ChatMessageResponse;
import com.drawgame.game.grpc.generated.GameStateResponse;
import com.drawgame.game.grpc.generated.PlayerScoreMessage;
import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.drawing.routing.DrawingRoomState;
import com.drawgame.realtime_gateway.drawing.routing.DrawingRoomStateCache;
import com.drawgame.realtime_gateway.grpc.ChatGrpcClient;
import com.drawgame.realtime_gateway.grpc.GameGrpcClient;
import com.drawgame.realtime_gateway.grpc.RoomGrpcClient;
import com.drawgame.room.grpc.generated.PlayerMessage;
import com.drawgame.room.grpc.generated.RoomResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameCommandHandler {

    private final GameGrpcClient gameGrpcClient;
    private final RoomGrpcClient roomGrpcClient;
    private final ChatGrpcClient chatGrpcClient;
    private final ConnectionManager connectionManager;
    private final DrawingRoomStateCache drawingRoomStateCache;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<String> handleCommand(String sessionId, JsonNode json) {
        String type = json.has("type") ? json.get("type").asText() : "";
        String requestId = extractRequestId(json);
        log.info("Handling command type '{}' (reqId: {}) from session {}", type, requestId, sessionId);

        return switch (type) {
            case "CREATE_ROOM" -> handleCreateRoom(sessionId, json, requestId);
            case "JOIN_ROOM" -> handleJoinRoom(sessionId, json, requestId);
            case "GET_ROOM" -> handleGetRoom(sessionId, json, requestId);
            case "LEAVE_ROOM" -> handleLeaveRoom(sessionId, json, requestId);
            case "START_GAME" -> handleStartGame(sessionId, json, requestId);
            case "GET_GAME_STATE" -> handleGetGameState(sessionId, json, requestId);
            case "SUBMIT_GUESS" -> handleSubmitGuess(sessionId, json, requestId);
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

    private Mono<String> handleCreateRoom(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        String playerId = extractString(node, "playerId", sessionId);
        String username = extractString(node, "username", "Player-" + sessionId.substring(0, Math.min(6, sessionId.length())));
        String roomName = extractString(node, "roomName", extractString(node, "name", username + "'s Room"));
        if (roomName == null || roomName.isBlank()) {
            roomName = username + "'s Room";
        }
        int maxPlayers = node.has("maxPlayers") ? node.get("maxPlayers").asInt() : (node.has("max_players") ? node.get("max_players").asInt() : 4);
        int totalRounds = node.has("totalRounds") ? node.get("totalRounds").asInt() : (node.has("roundCount") ? node.get("roundCount").asInt() : 5);
        int roundDuration = node.has("roundDuration") ? node.get("roundDuration").asInt() : (node.has("drawTime") ? node.get("drawTime").asInt() : 60);

        return roomGrpcClient.createRoom(playerId, username, roomName, maxPlayers, totalRounds, roundDuration)
                .map(response -> {
                    connectionManager.bindSession(sessionId, response.getRoomId(), playerId);
                    return createRoomSuccessJson("ROOM_CREATED", response, requestId);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "ROOM_CREATE_FAILED", e.getMessage())));
    }

    private Mono<String> handleJoinRoom(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = extractString(node, "roomId", "");
        String playerId = extractString(node, "playerId", sessionId);
        String username = extractString(node, "username", "Player-" + sessionId);

        return roomGrpcClient.joinRoom(roomId, playerId, username)
                .map(response -> {
                    connectionManager.bindSession(sessionId, response.getRoomId(), playerId);
                    String responseJson = createRoomSuccessJson("ROOM_JOINED", response, requestId);
                    connectionManager.broadcastToRoomExcept(response.getRoomId(), sessionId, createBroadcastJson("PLAYER_JOINED", response.getRoomId(), playerId, username));
                    return responseJson;
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "ROOM_JOIN_FAILED", e.getMessage())));
    }

    private Mono<String> handleGetRoom(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));

        return roomGrpcClient.getRoom(roomId)
                .map(response -> createRoomSuccessJson("ROOM_INFO", response, requestId))
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "GET_ROOM_FAILED", e.getMessage())));
    }

    private Mono<String> handleLeaveRoom(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        String playerId = extractString(node, "playerId", connectionManager.getPlayerId(sessionId));

        return roomGrpcClient.leaveRoom(roomId, playerId)
                .map(response -> {
                    String responseJson = createRoomSuccessJson("ROOM_LEFT", response, requestId);
                    connectionManager.broadcastToRoomExcept(roomId, sessionId, createBroadcastJson("PLAYER_LEFT", roomId, playerId, ""));
                    return responseJson;
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "LEAVE_ROOM_FAILED", e.getMessage())));
    }

    private Mono<String> handleStartGame(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        final String rawPlayerId = extractString(node, "playerId", connectionManager.getPlayerId(sessionId));
        final String playerId = (rawPlayerId != null && !rawPlayerId.isBlank()) ? rawPlayerId : sessionId;

        return gameGrpcClient.startGame(roomId, playerId)
                .map(gameState -> {
                    String stateJson = createGameStateJson("GAME_STARTED", gameState, requestId);
                    // Broadcast to other players in room (without requestId)
                    connectionManager.broadcastToRoomExcept(roomId, sessionId, createGameStateJson("GAME_STARTED", gameState, null));
                    // TV3: update drawing fast-path cache with the new drawer and round
                    updateDrawingCache(roomId, gameState);
                    return stateJson;
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "START_GAME_FAILED", e.getMessage())));
    }

    private Mono<String> handleGetGameState(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        final String rawPlayerId = extractString(node, "playerId", connectionManager.getPlayerId(sessionId));
        final String playerId = (rawPlayerId != null && !rawPlayerId.isBlank()) ? rawPlayerId : sessionId;

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

    private Mono<String> handleSubmitGuess(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        final String rawPlayerId = extractString(node, "playerId", connectionManager.getPlayerId(sessionId));
        final String playerId = (rawPlayerId != null && !rawPlayerId.isBlank()) ? rawPlayerId : sessionId;
        final String username = extractString(node, "username", "");
        final String guess = extractString(node, "guess", extractString(node, "content", ""));

        return gameGrpcClient.submitGuess(roomId, playerId, guess)
                .flatMap(response -> {
                    String status = response.getGuessStatus();
                    if ("CORRECT".equalsIgnoreCase(status)) {
                        // Broadcast safe event without secret text
                        String broadcastMsg = createGuessCorrectBroadcastJson(roomId, playerId, response.getScoreAwarded());
                        connectionManager.broadcastToRoomExcept(roomId, sessionId, broadcastMsg);

                        Map<String, Object> map = createGuessResultMap(roomId, playerId, status, response.getScoreAwarded(), requestId);
                        return Mono.just(toJson(map));
                    } else if ("WRONG".equalsIgnoreCase(status)) {
                        // Forward wrong guess to Chat Service to record & broadcast
                        return chatGrpcClient.sendMessage(roomId, playerId, username, guess)
                                .map(chatRes -> {
                                    String chatBroadcastJson = createChatMessageBroadcastJson(chatRes, null);
                                    connectionManager.broadcastToRoom(roomId, chatBroadcastJson);

                                    Map<String, Object> map = createGuessResultMap(roomId, playerId, status, response.getScoreAwarded(), requestId);
                                    return toJson(map);
                                })
                                .onErrorResume(e -> {
                                    Map<String, Object> map = createGuessResultMap(roomId, playerId, status, response.getScoreAwarded(), requestId);
                                    return Mono.just(toJson(map));
                                });
                    } else {
                        Map<String, Object> map = createGuessResultMap(roomId, playerId, status, response.getScoreAwarded(), requestId);
                        return Mono.just(toJson(map));
                    }
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, "SUBMIT_GUESS_FAILED", e.getMessage())));
    }

    private Mono<String> handleSendChat(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        final String rawPlayerId = extractString(node, "playerId", connectionManager.getPlayerId(sessionId));
        final String playerId = (rawPlayerId != null && !rawPlayerId.isBlank()) ? rawPlayerId : sessionId;
        final String username = extractString(node, "username", "");
        final String content = extractString(node, "content", "");

        return chatGrpcClient.sendMessage(roomId, playerId, username, content)
                .map(chatRes -> {
                    String chatBroadcastJson = createChatMessageBroadcastJson(chatRes, null);
                    connectionManager.broadcastToRoom(roomId, chatBroadcastJson);
                    return createChatMessageBroadcastJson(chatRes, requestId);
                })
                .onErrorResume(e -> Mono.just(createErrorJson(requestId, mapGrpcErrorCode(e), e.getMessage())));
    }

    private Mono<String> handleGetRecentChat(String sessionId, JsonNode json, String requestId) {
        JsonNode node = getPayloadOrRoot(json);
        final String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        final String rawPlayerId = extractString(node, "playerId", connectionManager.getPlayerId(sessionId));
        final String playerId = (rawPlayerId != null && !rawPlayerId.isBlank()) ? rawPlayerId : sessionId;
        final int limit = node.has("limit") ? node.get("limit").asInt() : 50;

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

        Map<String, Object> event = new HashMap<>();
        event.put("type", "DRAW_EVENT");
        event.put("roomId", roomId);
        event.put("playerId", extractString(node, "drawerId", connectionManager.getPlayerId(sessionId)));
        event.put("point", node.get("point"));
        connectionManager.broadcastToRoomExcept(roomId, sessionId, toJson(event));
        return Mono.empty();
    }

    private Mono<String> handleDrawBatch(String sessionId, JsonNode json) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        if (roomId == null || roomId.isBlank() || !node.has("points") || !node.get("points").isArray()) {
            return Mono.empty();
        }

        Map<String, Object> event = new HashMap<>();
        event.put("type", "DRAW_BATCH_EVENT");
        event.put("roomId", roomId);
        event.put("playerId", extractString(node, "drawerId", connectionManager.getPlayerId(sessionId)));
        event.put("points", node.get("points"));
        connectionManager.broadcastToRoomExcept(roomId, sessionId, toJson(event));
        return Mono.empty();
    }

    private Mono<String> handleClearCanvas(String sessionId, JsonNode json) {
        JsonNode node = getPayloadOrRoot(json);
        String roomId = extractString(node, "roomId", connectionManager.getRoomId(sessionId));
        if (roomId == null || roomId.isBlank()) {
            return Mono.empty();
        }

        connectionManager.broadcastToRoomExcept(roomId, sessionId,
                createBroadcastJson("CANVAS_CLEARED", roomId,
                        extractString(node, "drawerId", connectionManager.getPlayerId(sessionId)), ""));
        return Mono.empty();
    }

    private JsonNode getPayloadOrRoot(JsonNode json) {
        return json.has("payload") ? json.get("payload") : json;
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

    private String createErrorJson(String requestId, String errorCode, String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "ERROR");
        if (requestId != null && !requestId.isBlank()) {
            map.put("requestId", requestId);
        }
        map.put("code", errorCode);
        map.put("message", message);
        Map<String, Object> errObj = new HashMap<>();
        errObj.put("code", errorCode);
        errObj.put("message", message);
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

        // Broadcast GAME_FINISHED to all players in room (no exclusion — everyone should know)
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "GAME_FINISHED");
        payload.put("roomId", roomId);
        if (requestId != null) payload.put("requestId", requestId);
        connectionManager.broadcastToRoom(roomId, toJson(payload));

        Map<String, Object> response = new HashMap<>();
        response.put("type", "GAME_FINISHED_ACK");
        if (requestId != null) response.put("requestId", requestId);
        return Mono.just(toJson(response));
    }
}


