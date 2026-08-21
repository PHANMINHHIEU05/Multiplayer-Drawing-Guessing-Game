package com.drawgame.chat.exception;

public class ChatPlayerNotInRoomException extends RuntimeException {
    public ChatPlayerNotInRoomException(String message) {
        super(message);
    }
}
