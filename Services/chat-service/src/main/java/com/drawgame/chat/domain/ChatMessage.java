package com.drawgame.chat.domain;

import java.time.Instant;

public class ChatMessage {
    private String messageId;
    private String roomId;
    private String playerId;
    private String username;
    private String content;
    private ChatMessageType type;
    private Instant createdAt;

    public ChatMessage() {}

    public ChatMessage(String messageId, String roomId, String playerId, String username, String content, ChatMessageType type, Instant createdAt) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.playerId = playerId;
        this.username = username;
        this.content = content;
        this.type = type;
        this.createdAt = createdAt;
    }

    public static ChatMessageBuilder builder() {
        return new ChatMessageBuilder();
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public ChatMessageType getType() { return type; }
    public void setType(ChatMessageType type) { this.type = type; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static class ChatMessageBuilder {
        private String messageId;
        private String roomId;
        private String playerId;
        private String username;
        private String content;
        private ChatMessageType type;
        private Instant createdAt;

        public ChatMessageBuilder messageId(String messageId) { this.messageId = messageId; return this; }
        public ChatMessageBuilder roomId(String roomId) { this.roomId = roomId; return this; }
        public ChatMessageBuilder playerId(String playerId) { this.playerId = playerId; return this; }
        public ChatMessageBuilder username(String username) { this.username = username; return this; }
        public ChatMessageBuilder content(String content) { this.content = content; return this; }
        public ChatMessageBuilder type(ChatMessageType type) { this.type = type; return this; }
        public ChatMessageBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public ChatMessage build() {
            return new ChatMessage(messageId, roomId, playerId, username, content, type, createdAt);
        }
    }
}
