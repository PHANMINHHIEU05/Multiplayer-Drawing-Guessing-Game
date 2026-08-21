package com.drawgame.game.grpc.generated;

public interface PlayerScoreMessageOrBuilder extends
    com.google.protobuf.MessageOrBuilder {

  java.lang.String getPlayerId();
  com.google.protobuf.ByteString getPlayerIdBytes();

  java.lang.String getUsername();
  com.google.protobuf.ByteString getUsernameBytes();

  int getScore();

  boolean getHasGuessed();
}
