package com.drawgame.chat.grpc;

import com.drawgame.chat.domain.ChatMessage;
import com.drawgame.chat.domain.ChatMessageType;
import com.drawgame.chat.grpc.generated.ChatMessageResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatGrpcMapperTest {

    private final ChatGrpcMapper mapper = Mappers.getMapper(ChatGrpcMapper.class);

    @Test
    void testToResponse_PreservesVietnameseUnicodeAndEpochMs() {
        Instant now = Instant.now();
        ChatMessage msg = ChatMessage.builder()
                .messageId("msg-1")
                .roomId("room-1")
                .playerId("player-1")
                .username("Nam Đẹp Trai 🚀")
                .content("con mèo đang bay 🐱✨")
                .type(ChatMessageType.USER)
                .createdAt(now)
                .build();

        ChatMessageResponse response = mapper.toResponse(msg);

        assertEquals("msg-1", response.getMessageId());
        assertEquals("room-1", response.getRoomId());
        assertEquals("player-1", response.getPlayerId());
        assertEquals("Nam Đẹp Trai 🚀", response.getUsername());
        assertEquals("con mèo đang bay 🐱✨", response.getContent());
        assertEquals("USER", response.getType());
        assertEquals(now.toEpochMilli(), response.getCreatedAtEpochMs());
    }
}
