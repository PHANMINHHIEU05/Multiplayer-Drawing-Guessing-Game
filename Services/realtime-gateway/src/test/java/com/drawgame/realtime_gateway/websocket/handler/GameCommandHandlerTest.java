package com.drawgame.realtime_gateway.websocket.handler;

import com.drawgame.chat.grpc.generated.ChatMessageResponse;
import com.drawgame.chat.grpc.generated.GetRecentMessagesResponse;
import com.drawgame.game.grpc.generated.GameStateResponse;
import com.drawgame.game.grpc.generated.GuessResponse;
import com.drawgame.room.grpc.generated.PlayerMessage;
import com.drawgame.room.grpc.generated.RoomResponse;
import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.drawing.routing.DrawingRoomStateCache;
import com.drawgame.realtime_gateway.grpc.ChatGrpcClient;
import com.drawgame.realtime_gateway.grpc.GameGrpcClient;
import com.drawgame.realtime_gateway.grpc.RoomGrpcClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameCommandHandlerTest {

    @Mock
    private GameGrpcClient gameGrpcClient;

    @Mock
    private RoomGrpcClient roomGrpcClient;

    @Mock
    private ChatGrpcClient chatGrpcClient;

    @Mock
    private ConnectionManager connectionManager;

    @Mock
    private DrawingRoomStateCache drawingRoomStateCache;

    private GameCommandHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** TV8: real (test-secret) token service so resume tests exercise actual verification. */
    private final com.drawgame.realtime_gateway.security.GameSessionTokenService tokenService =
            new com.drawgame.realtime_gateway.security.GameSessionTokenService(
                    "test-secret-that-is-long-enough-for-hs256-0123456789", 60);
    private final com.drawgame.realtime_gateway.security.SessionRateLimiter rateLimiter =
            new com.drawgame.realtime_gateway.security.SessionRateLimiter(1000, 1000, 100000, 1000, 500);

    @BeforeEach
    void setUp() {
        // TV8: full constructor — real token service (test secret) + generous limiter so
        // existing behavioral tests stay focused on their scenarios.
        handler = new GameCommandHandler(gameGrpcClient, roomGrpcClient, chatGrpcClient,
                connectionManager, drawingRoomStateCache, null, null,
                tokenService, rateLimiter,
                new com.drawgame.realtime_gateway.security.InputValidator(32, 64, 128),
                "test-gateway");
        // TV8: handlers derive identity from the bound session — stub the binding for
        // every test (unbound sessions are rejected by design now).
        lenient().when(connectionManager.getRoomId("session-1")).thenReturn("room-1");
        lenient().when(connectionManager.getPlayerId("session-1")).thenReturn("player-1");
    }


    @Test
    void handleSendChat_Success_BroadcastsChatMessageToRoom() throws Exception {
        String jsonStr = """
            {
                "type": "SEND_CHAT",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "player-1",
                    "username": "Minh",
                    "content": "Xin chào"
                }
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        ChatMessageResponse chatResponse = ChatMessageResponse.newBuilder()
                .setMessageId("msg-1")
                .setRoomId("room-1")
                .setPlayerId("player-1")
                .setUsername("Minh")
                .setContent("Xin chào")
                .setType("USER")
                .setCreatedAtEpochMs(1700000000000L)
                .build();

        when(chatGrpcClient.sendMessage("room-1", "player-1", "", "Xin chào"))
                .thenReturn(Mono.just(chatResponse));

        Mono<String> resultMono = handler.handleCommand("session-1", json);

        StepVerifier.create(resultMono)
                .assertNext(res -> {
                    assertTrue(res.contains("CHAT_MESSAGE"));
                    assertTrue(res.contains("Xin chào"));
                })
                .verifyComplete();

        verify(connectionManager).broadcastToRoom(eq("room-1"), anyString());
    }

    @Test
    void handleSendChat_RateLimited_ReturnsErrorJson() throws Exception {
        String jsonStr = """
            {
                "type": "SEND_CHAT",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "player-1",
                    "content": "spam"
                }
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        when(chatGrpcClient.sendMessage(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED.withDescription("Rate limit"))));

        Mono<String> resultMono = handler.handleCommand("session-1", json);

        StepVerifier.create(resultMono)
                .assertNext(res -> {
                    assertTrue(res.contains("CHAT_RATE_LIMITED"));
                })
                .verifyComplete();
    }

    @Test
    void handleGetRecentChat_Success_ReturnsHistory() throws Exception {
        String jsonStr = """
            {
                "type": "GET_RECENT_CHAT",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "player-1",
                    "limit": 10
                }
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        GetRecentMessagesResponse historyRes = GetRecentMessagesResponse.newBuilder()
                .addMessages(ChatMessageResponse.newBuilder()
                        .setMessageId("m1")
                        .setRoomId("room-1")
                        .setPlayerId("player-1")
                        .setUsername("Minh")
                        .setContent("Hi")
                        .setType("USER")
                        .setCreatedAtEpochMs(1000L)
                        .build())
                .build();

        when(chatGrpcClient.getRecentMessages("room-1", "player-1", 10))
                .thenReturn(Mono.just(historyRes));

        Mono<String> resultMono = handler.handleCommand("session-1", json);

        StepVerifier.create(resultMono)
                .assertNext(res -> {
                    assertTrue(res.contains("CHAT_HISTORY"));
                    assertTrue(res.contains("Hi"));
                })
                .verifyComplete();
    }

    @Test
    void handleSubmitGuess_CorrectGuess_BroadcastsSafeEventWithoutSecretWord() throws Exception {
        String jsonStr = """
            {
                "type": "SUBMIT_GUESS",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "player-1",
                    "guess": "máy bay"
                }
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        GuessResponse guessRes = GuessResponse.newBuilder()
                .setRoomId("room-1")
                .setPlayerId("player-1")
                .setGuessStatus("CORRECT")
                .setScoreAwarded(10)
                .build();

        when(gameGrpcClient.submitGuess("room-1", "player-1", "máy bay"))
                .thenReturn(Mono.just(guessRes));

        GameStateResponse gameState = GameStateResponse.newBuilder()
                .setRoomId("room-1")
                .setDrawerId("player-2")
                .setCurrentRound(2)
                .setStatus("PLAYING")
                .build();
        when(gameGrpcClient.getGameState("room-1", "player-1"))
                .thenReturn(Mono.just(gameState));

        Mono<String> resultMono = handler.handleCommand("session-1", json);

        StepVerifier.create(resultMono)
                .assertNext(res -> {
                    assertTrue(res.contains("GUESS_RESULT"));
                    assertTrue(res.contains("CORRECT"));
                    assertFalse(res.contains("máy bay")); // Secret word not leaked in response
                })
                .verifyComplete();

        // Broadcasts PLAYER_GUESSED_CORRECTLY without secret word
        verify(connectionManager).broadcastToRoomExcept(eq("room-1"), eq("session-1"), contains("PLAYER_GUESSED_CORRECTLY"));
        // Drawing cache refreshed on correct guess
        verify(drawingRoomStateCache).update(eq("room-1"), any());
        // Chat service MUST NOT be called for correct guess
        verifyNoInteractions(chatGrpcClient);
    }

    @Test
    void handleLeaveRoom_UnbindsSession_AndEvictsCacheIfRoomEmpty() throws Exception {
        String jsonStr = """
            {
                "type": "LEAVE_ROOM",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "player-1"
                },
                "requestId": "req-leave"
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        RoomResponse emptyRoom = RoomResponse.newBuilder()
                .setRoomId("room-1")
                .setStatus("WAITING")
                .build(); // playersList is empty

        when(roomGrpcClient.leaveRoom("room-1", "player-1")).thenReturn(Mono.just(emptyRoom));

        StepVerifier.create(handler.handleCommand("session-1", json))
                .assertNext(res -> {
                    assertTrue(res.contains("ROOM_LEFT"));
                    assertTrue(res.contains("req-leave"));
                })
                .verifyComplete();

        verify(connectionManager).unbindSession("session-1");
        verify(drawingRoomStateCache).remove("room-1");
    }

    @Test
    void handleSubmitGuess_WrongGuess_ForwardsToChatServiceAndBroadcasts() throws Exception {
        String jsonStr = """
            {
                "type": "SUBMIT_GUESS",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "player-1",
                    "username": "Minh",
                    "guess": "con thỏ"
                }
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        GuessResponse guessRes = GuessResponse.newBuilder()
                .setRoomId("room-1")
                .setPlayerId("player-1")
                .setGuessStatus("WRONG")
                .setScoreAwarded(0)
                .build();

        ChatMessageResponse chatRes = ChatMessageResponse.newBuilder()
                .setMessageId("m-wrong")
                .setRoomId("room-1")
                .setPlayerId("player-1")
                .setUsername("Minh")
                .setContent("con thỏ")
                .setType("USER")
                .setCreatedAtEpochMs(1000L)
                .build();

        when(gameGrpcClient.submitGuess("room-1", "player-1", "con thỏ"))
                .thenReturn(Mono.just(guessRes));
        when(chatGrpcClient.sendMessage("room-1", "player-1", "", "con thỏ"))
                .thenReturn(Mono.just(chatRes));

        Mono<String> resultMono = handler.handleCommand("session-1", json);

        StepVerifier.create(resultMono)
                .assertNext(res -> {
                    assertTrue(res.contains("GUESS_RESULT"));
                    assertTrue(res.contains("WRONG"));
                })
                .verifyComplete();

        // Chat message broadcasted for wrong guess
        verify(chatGrpcClient).sendMessage("room-1", "player-1", "", "con thỏ");
        verify(connectionManager).broadcastToRoom(eq("room-1"), contains("CHAT_MESSAGE"));
    }

    @Test
    void handleGameFinished_ClearsDrawingCacheAndBroadcasts() throws Exception {
        String jsonStr = """
            {
                "type": "GAME_FINISHED",
                "payload": {
                    "roomId": "room-1"
                },
                "requestId": "req-999"
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        Mono<String> resultMono = handler.handleCommand("session-1", json);

        StepVerifier.create(resultMono)
                .assertNext(res -> {
                    assertTrue(res.contains("GAME_FINISHED_ACK"));
                    assertTrue(res.contains("req-999"));
                })
                .verifyComplete();

        verify(drawingRoomStateCache).remove("room-1");
        verify(connectionManager).broadcastToRoom(eq("room-1"), contains("GAME_FINISHED"));
    }

    @Test
    void handleResumeSession_PlayingRoom_BindsSessionWithoutJoining() throws Exception {
        // TV8: valid signed token for player-1/room-1
        String token = tokenService.issue("player-1", "room-1");
        String jsonStr = """
            {
                "type": "RESUME_SESSION",
                "requestId": "req-resume",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "player-1",
                    "token": "%s"
                }
            }
            """.formatted(token);
        JsonNode json = objectMapper.readTree(jsonStr);

        RoomResponse room = RoomResponse.newBuilder()
                .setRoomId("room-1")
                .setStatus("PLAYING")
                .addPlayers(PlayerMessage.newBuilder()
                        .setPlayerId("player-1")
                        .setUsername("Minh")
                        .build())
                .build();

        when(roomGrpcClient.getRoom("room-1")).thenReturn(Mono.just(room));

        StepVerifier.create(handler.handleCommand("new-session", json))
                .assertNext(res -> {
                    assertTrue(res.contains("SESSION_RESUMED"));
                    assertTrue(res.contains("PLAYING"));
                    assertTrue(res.contains("req-resume"));
                    assertTrue(res.contains("sessionToken")); // TV8: rotated credential
                })
                .verifyComplete();

        verify(connectionManager).bindSession("new-session", "room-1", "player-1");
        verify(roomGrpcClient, never()).joinRoom(anyString(), anyString(), anyString());
    }

    @Test
    void handleResumeSession_NonMember_DoesNotBindSession() throws Exception {
        // Valid token for player-1, but membership was removed (explicit leave)
        String token = tokenService.issue("player-1", "room-1");
        String jsonStr = """
            {
                "type": "RESUME_SESSION",
                "requestId": "req-resume",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "player-1",
                    "token": "%s"
                }
            }
            """.formatted(token);
        JsonNode json = objectMapper.readTree(jsonStr);

        RoomResponse room = RoomResponse.newBuilder()
                .setRoomId("room-1")
                .setStatus("PLAYING")
                .addPlayers(PlayerMessage.newBuilder()
                        .setPlayerId("player-2")
                        .build())
                .build();

        when(roomGrpcClient.getRoom("room-1")).thenReturn(Mono.just(room));

        StepVerifier.create(handler.handleCommand("new-session", json))
                .assertNext(res -> assertTrue(res.contains("PLAYER_NOT_IN_ROOM")))
                .verifyComplete();

        verify(connectionManager, never()).bindSession(anyString(), anyString(), anyString());
    }

    // ─── TV8 security tests ───────────────────────────────────────────────

    @Test
    void resume_NoToken_IsRejected_NoInsecureFallback() throws Exception {
        String jsonStr = """
            {
                "type": "RESUME_SESSION",
                "payload": { "roomId": "room-1", "playerId": "player-1" }
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        StepVerifier.create(handler.handleCommand("new-session", json))
                .assertNext(res -> assertTrue(res.contains("AUTH_REQUIRED")))
                .verifyComplete();

        verify(connectionManager, never()).bindSession(anyString(), anyString(), anyString());
        verify(roomGrpcClient, never()).getRoom(anyString());
    }

    @Test
    void resume_AttackerTokenWithVictimPlayerId_CannotBecomeVictim() throws Exception {
        // SEC-008: attacker holds a valid token for THEMSELVES (player-evil) but
        // claims playerId=player-1 (victim) in the payload.
        String attackerToken = tokenService.issue("player-evil", "room-1");
        String jsonStr = """
            {
                "type": "RESUME_SESSION",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "player-1",
                    "token": "%s"
                }
            }
            """.formatted(attackerToken);
        JsonNode json = objectMapper.readTree(jsonStr);

        StepVerifier.create(handler.handleCommand("attacker-session", json))
                .assertNext(res -> {
                    assertTrue(res.contains("INVALID_SESSION_TOKEN"));
                    assertTrue(res.contains("does not match"));
                })
                .verifyComplete();

        // No binding, no room lookup, no session replacement — victim untouched.
        verify(connectionManager, never()).bindSession(anyString(), anyString(), anyString());
        verify(roomGrpcClient, never()).getRoom(anyString());
    }

    @Test
    void resume_TamperedToken_IsRejected() throws Exception {
        // SEC-005: take a valid token and flip the tail
        String token = tokenService.issue("player-1", "room-1");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        String jsonStr = """
            {
                "type": "RESUME_SESSION",
                "payload": { "roomId": "room-1", "token": "%s" }
            }
            """.formatted(tampered);
        JsonNode json = objectMapper.readTree(jsonStr);

        StepVerifier.create(handler.handleCommand("new-session", json))
                .assertNext(res -> assertTrue(res.contains("INVALID_SESSION_TOKEN")))
                .verifyComplete();

        verify(connectionManager, never()).bindSession(anyString(), anyString(), anyString());
    }

    @Test
    void resume_TokenForOtherRoom_IsRejected() throws Exception {
        // SEC-017: credential for room-A used against room-B
        String token = tokenService.issue("player-1", "room-A");
        String jsonStr = """
            {
                "type": "RESUME_SESSION",
                "payload": { "roomId": "room-B", "token": "%s" }
            }
            """.formatted(token);
        JsonNode json = objectMapper.readTree(jsonStr);

        StepVerifier.create(handler.handleCommand("new-session", json))
                .assertNext(res -> assertTrue(res.contains("ROOM_SCOPE_MISMATCH")))
                .verifyComplete();

        verify(roomGrpcClient, never()).getRoom(anyString());
    }

    @Test
    void submitGuess_SpoofedPlayerId_Ignored_BoundIdentityUsed() throws Exception {
        // SEC-015: payload claims another player — bound identity (player-1) wins
        com.drawgame.game.grpc.generated.GuessResponse guessResponse =
                com.drawgame.game.grpc.generated.GuessResponse.newBuilder()
                        .setRoomId("room-1")
                        .setPlayerId("player-1")
                        .setGuessStatus("WRONG")
                        .setScoreAwarded(0)
                        .build();
        when(gameGrpcClient.submitGuess("room-1", "player-1", "guess")).thenReturn(Mono.just(guessResponse));
        // WRONG guesses are echoed through Chat Service — stub the forward
        when(chatGrpcClient.sendMessage(eq("room-1"), eq("player-1"), anyString(), eq("guess")))
                .thenReturn(Mono.just(com.drawgame.chat.grpc.generated.ChatMessageResponse.newBuilder()
                        .setMessageId("m1").setRoomId("room-1").setPlayerId("player-1")
                        .setUsername("Minh").setContent("guess").setType("USER").build()));

        String jsonStr = """
            {
                "type": "SUBMIT_GUESS",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "another-player",
                    "guess": "guess"
                }
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        StepVerifier.create(handler.handleCommand("session-1", json))
                .assertNext(res -> {
                    assertTrue(res.contains("GUESS_RESULT"));
                    assertTrue(res.contains("player-1")); // authoritative identity
                    assertTrue(!res.contains("another-player"));
                })
                .verifyComplete();

        // The gRPC call used the BOUND identity, never the spoofed one
        verify(gameGrpcClient).submitGuess("room-1", "player-1", "guess");
        verify(gameGrpcClient, never()).submitGuess(anyString(), eq("another-player"), anyString());
    }

    @Test
    void sendChat_SpoofedSender_BoundIdentityUsed() throws Exception {
        // SEC-016: username field cannot override authoritative sender
        ChatMessageResponse chatResponse = ChatMessageResponse.newBuilder()
                .setMessageId("m1").setRoomId("room-1").setPlayerId("player-1")
                .setUsername("Minh").setContent("hello").setType("USER").build();
        when(chatGrpcClient.sendMessage("room-1", "player-1", "", "hello"))
                .thenReturn(Mono.just(chatResponse));

        String jsonStr = """
            {
                "type": "SEND_CHAT",
                "payload": {
                    "playerId": "another-player",
                    "username": "FakeName",
                    "content": "hello"
                }
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        StepVerifier.create(handler.handleCommand("session-1", json))
                .assertNext(res -> assertTrue(res.contains("CHAT_MESSAGE")))
                .verifyComplete();

        verify(chatGrpcClient).sendMessage("room-1", "player-1", "", "hello");
        verify(chatGrpcClient, never()).sendMessage(anyString(), eq("another-player"), anyString(), anyString());
    }

    @Test
    void unboundSession_ProtectedCommand_Rejected() throws Exception {
        String jsonStr = """
            {
                "type": "GET_GAME_STATE",
                "payload": { "roomId": "room-1" }
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        StepVerifier.create(handler.handleCommand("unknown-session", json))
                .assertNext(res -> assertTrue(res.contains("INVALID_SESSION")))
                .verifyComplete();

        verify(gameGrpcClient, never()).getGameState(anyString(), anyString());
    }
}
