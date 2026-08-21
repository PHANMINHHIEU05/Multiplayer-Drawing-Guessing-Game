package com.drawgame.game.grpc.generated;

public interface GuessResponseOrBuilder extends
    com.google.protobuf.MessageOrBuilder {

  java.lang.String getRoomId();
  com.google.protobuf.ByteString getRoomIdBytes();

  java.lang.String getPlayerId();
  com.google.protobuf.ByteString getPlayerIdBytes();

  java.lang.String getGuessStatus();
  com.google.protobuf.ByteString getGuessStatusBytes();

  int getScoreAwarded();
}
