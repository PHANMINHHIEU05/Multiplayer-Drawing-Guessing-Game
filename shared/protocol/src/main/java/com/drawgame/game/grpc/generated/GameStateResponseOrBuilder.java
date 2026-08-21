package com.drawgame.game.grpc.generated;

public interface GameStateResponseOrBuilder extends
    com.google.protobuf.MessageOrBuilder {

  java.lang.String getRoomId();
  com.google.protobuf.ByteString getRoomIdBytes();

  java.lang.String getStatus();
  com.google.protobuf.ByteString getStatusBytes();

  int getCurrentRound();

  int getTotalRounds();

  java.lang.String getDrawerId();
  com.google.protobuf.ByteString getDrawerIdBytes();

  long getRoundStartedAt();

  long getRoundEndsAt();

  java.util.List<com.drawgame.game.grpc.generated.PlayerScoreMessage> 
      getScoresList();
  com.drawgame.game.grpc.generated.PlayerScoreMessage getScores(int index);
  int getScoresCount();

  java.lang.String getHint();
  com.google.protobuf.ByteString getHintBytes();

  java.lang.String getSecretWord();
  com.google.protobuf.ByteString getSecretWordBytes();
}
