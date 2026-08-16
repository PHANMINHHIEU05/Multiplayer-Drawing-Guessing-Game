package com.drawgame.room.exception;

public class PlayerAlreadyInRoomException extends RuntimeException {
    public PlayerAlreadyInRoomException(String message) {
        super(message);
    }
}
