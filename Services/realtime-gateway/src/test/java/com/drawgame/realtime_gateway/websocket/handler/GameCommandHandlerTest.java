package com.drawgame.realtime_gateway.websocket.handler;

import com.drawgame.chat.grpc.generated.ChatMessageResponse;
import com.drawgame.chat.grpc.generated.GetRecentMessagesResponse;
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

    @BeforeEach
    void setUp() {
        handler = new GameCommandHandler(gameGrpcClient, roomGrpcClient, chatGrpcClient, connectionManager, drawingRoomStateCache);
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

        when(chatGrpcClient.sendMessage("room-1", "player-1", "Minh", "Xin chào"))
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
        // Chat service MUST NOT be called for correct guess
        verifyNoInteractions(chatGrpcClient);
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
        when(chatGrpcClient.sendMessage("room-1", "player-1", "Minh", "con thỏ"))
                .thenReturn(Mono.just(chatRes));

        Mono<String> resultMono = handler.handleCommand("session-1", json);

        StepVerifier.create(resultMono)
                .assertNext(res -> {
                    assertTrue(res.contains("GUESS_RESULT"));
                    assertTrue(res.contains("WRONG"));
                })
                .verifyComplete();

        // Chat message broadcasted for wrong guess
        verify(chatGrpcClient).sendMessage("room-1", "player-1", "Minh", "con thỏ");
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
        String jsonStr = """
            {
                "type": "RESUME_SESSION",
                "requestId": "req-resume",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "player-1"
                }
            }
            """;
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
                })
                .verifyComplete();

        verify(connectionManager).bindSession("new-session", "room-1", "player-1");
        verify(roomGrpcClient, never()).joinRoom(anyString(), anyString(), anyString());
    }

    @Test
    void handleResumeSession_NonMember_DoesNotBindSession() throws Exception {
        String jsonStr = """
            {
                "type": "RESUME_SESSION",
                "requestId": "req-resume",
                "payload": {
                    "roomId": "room-1",
                    "playerId": "player-2"
                }
            }
            """;
        JsonNode json = objectMapper.readTree(jsonStr);

        RoomResponse room = RoomResponse.newBuilder()
                .setRoomId("room-1")
                .setStatus("PLAYING")
                .addPlayers(PlayerMessage.newBuilder()
                        .setPlayerId("player-1")
                        .build())
                .build();

        when(roomGrpcClient.getRoom("room-1")).thenReturn(Mono.just(room));

        StepVerifier.create(handler.handleCommand("new-session", json))
                .assertNext(res -> assertTrue(res.contains("PLAYER_NOT_IN_ROOM")))
                .verifyComplete();

        verify(connectionManager, never()).bindSession(anyString(), anyString(), anyString());
    }
}
