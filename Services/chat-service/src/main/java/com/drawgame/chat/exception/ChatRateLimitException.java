package com.drawgame.chat.exception;

public class ChatRateLimitException extends RuntimeException {
    public ChatRateLimitException(String message) {
        super(message);
    }
}
