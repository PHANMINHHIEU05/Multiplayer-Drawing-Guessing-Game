package com.drawgame.chat.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String messageId;
    private String roomId;
    private String playerId;
    private String username;
    private String content;
    private ChatMessageType type;
    private Instant createdAt;
}
