package com.drawgame.game.grpc.generated;

public final class Game {
  private Game() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_game_StartGameRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_game_StartGameRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_game_GetGameStateRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_game_GetGameStateRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_game_SubmitGuessRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_game_SubmitGuessRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_game_PlayerScoreMessage_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_game_PlayerScoreMessage_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_game_GuessResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_game_GuessResponse_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_game_GameStateResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_game_GameStateResponse_fieldAccessorTable;

  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\ngame.proto\022\004game\"<\n\020StartGameRequest\022\017" +
      "\n\007room_id\020\001 \001(\t\022\034\n\024requester_player_id\020\002" +
      " \001(\t\"A\n\023GetGameStateRequest\022\017\n\007room_id\020" +
      "\001 \001(\t\022\031\n\021viewer_player_id\020\002 \001(\t\"D\n\022Submi" +
      "tGuessRequest\022\017\n\007room_id\020\001 \001(\t\022\021\n\tplayer" +
      "_id\020\002 \001(\t\022\007\n\005guess\020\003 \001(\t\"\\\n\022PlayerScoreM" +
      "essage\022\021\n\tplayer_id\020\001 \001(\t\022\020\n\010username\020\002 " +
      "\001(\t\022\r\n\005score\020\003 \001(\005\022\023\n\013has_guessed\020\004 \001(\010" +
      "\"_\n\rGuessResponse\022\017\n\007room_id\020\001 \001(\t\022\021\n\tpl" +
      "ayer_id\020\002 \001(\t\022\024\n\014guess_status\020\003 \001(\t\022\025\n\rs" +
      "core_awarded\020\004 \001(\005\"\363\001\n\021GameStateResponse" +
      "\022\017\n\007room_id\020\001 \001(\t\022\016\n\006status\020\002 \001(\t\022\025\n\rcur" +
      "rent_round\020\003 \001(\005\022\024\n\014total_rounds\020\004 \001(\005\022\021" +
      "\n\tdrawer_id\020\005 \001(\t\022\030\n\020round_started_at\020\006 " +
      "\001(\003\022\025\n\rround_ends_at\020\007 \001(\003\022(\n\006scores\020\010 \003" +
      "(\0132\030.game.PlayerScoreMessage\022\014\n\004hint\020\t \001" +
      "(\t\022\023\n\013secret_word\020\n \001(\t2\313\001\n\013GameService\022" +
      ";\n\tStartGame\022\026.game.StartGameRequest\032\027.g" +
      "ame.GameStateResponse\"\000\022A\n\014GetGameState\022" +
      "\033.game.GetGameStateRequest\032\027.game.GameSt" +
      "ateResponse\"\000\022<\n\013SubmitGuess\022\030.game.Subm" +
      "itGuessRequest\032\023.game.GuessResponse\"\000B$\n" +
      " com.drawgame.game.grpc.generatedP\001b\006pro" +
      "to3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_game_StartGameRequest_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_game_StartGameRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_game_StartGameRequest_descriptor,
        new java.lang.String[] { "RoomId", "RequesterPlayerId", });
    internal_static_game_GetGameStateRequest_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_game_GetGameStateRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_game_GetGameStateRequest_descriptor,
        new java.lang.String[] { "RoomId", "ViewerPlayerId", });
    internal_static_game_SubmitGuessRequest_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_game_SubmitGuessRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_game_SubmitGuessRequest_descriptor,
        new java.lang.String[] { "RoomId", "PlayerId", "Guess", });
    internal_static_game_PlayerScoreMessage_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_game_PlayerScoreMessage_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_game_PlayerScoreMessage_descriptor,
        new java.lang.String[] { "PlayerId", "Username", "Score", "HasGuessed", });
    internal_static_game_GuessResponse_descriptor =
      getDescriptor().getMessageTypes().get(4);
    internal_static_game_GuessResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_game_GuessResponse_descriptor,
        new java.lang.String[] { "RoomId", "PlayerId", "GuessStatus", "ScoreAwarded", });
    internal_static_game_GameStateResponse_descriptor =
      getDescriptor().getMessageTypes().get(5);
    internal_static_game_GameStateResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_game_GameStateResponse_descriptor,
        new java.lang.String[] { "RoomId", "Status", "CurrentRound", "TotalRounds", "DrawerId", "RoundStartedAt", "RoundEndsAt", "Scores", "Hint", "SecretWord", });
    descriptor.resolveAllFeaturesImmutable();
  }

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
}
