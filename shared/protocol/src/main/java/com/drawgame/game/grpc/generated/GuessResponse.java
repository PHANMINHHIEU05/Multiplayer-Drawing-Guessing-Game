package com.drawgame.game.grpc.generated;

public final class GuessResponse extends
    com.google.protobuf.GeneratedMessage implements
    GuessResponseOrBuilder {
private static final long serialVersionUID = 0L;

  private GuessResponse(com.google.protobuf.GeneratedMessage.Builder<?> builder) { super(builder); }
  private GuessResponse() {
    roomId_ = "";
    playerId_ = "";
    guessStatus_ = "";
  }

  public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_GuessResponse_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_GuessResponse_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.drawgame.game.grpc.generated.GuessResponse.class, com.drawgame.game.grpc.generated.GuessResponse.Builder.class);
  }

  private volatile java.lang.Object roomId_ = "";
  public java.lang.String getRoomId() {
    java.lang.Object ref = roomId_;
    if (ref instanceof java.lang.String) return (java.lang.String) ref;
    else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      roomId_ = s;
      return s;
    }
  }
  public com.google.protobuf.ByteString getRoomIdBytes() {
    java.lang.Object ref = roomId_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      roomId_ = b;
      return b;
    } else return (com.google.protobuf.ByteString) ref;
  }

  private volatile java.lang.Object playerId_ = "";
  public java.lang.String getPlayerId() {
    java.lang.Object ref = playerId_;
    if (ref instanceof java.lang.String) return (java.lang.String) ref;
    else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      playerId_ = s;
      return s;
    }
  }
  public com.google.protobuf.ByteString getPlayerIdBytes() {
    java.lang.Object ref = playerId_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      playerId_ = b;
      return b;
    } else return (com.google.protobuf.ByteString) ref;
  }

  private volatile java.lang.Object guessStatus_ = "";
  public java.lang.String getGuessStatus() {
    java.lang.Object ref = guessStatus_;
    if (ref instanceof java.lang.String) return (java.lang.String) ref;
    else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      guessStatus_ = s;
      return s;
    }
  }
  public com.google.protobuf.ByteString getGuessStatusBytes() {
    java.lang.Object ref = guessStatus_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      guessStatus_ = b;
      return b;
    } else return (com.google.protobuf.ByteString) ref;
  }

  private int scoreAwarded_;
  public int getScoreAwarded() { return scoreAwarded_; }

  @java.lang.Override
  public final boolean isInitialized() { return true; }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output) throws java.io.IOException {
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(roomId_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 1, roomId_);
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(playerId_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 2, playerId_);
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(guessStatus_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 3, guessStatus_);
    }
    if (scoreAwarded_ != 0) {
      output.writeInt32(4, scoreAwarded_);
    }
    getUnknownFields().writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;
    size = 0;
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(roomId_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(1, roomId_);
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(playerId_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(2, playerId_);
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(guessStatus_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(3, guessStatus_);
    }
    if (scoreAwarded_ != 0) {
      size += com.google.protobuf.CodedOutputStream.computeInt32Size(4, scoreAwarded_);
    }
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() { return DEFAULT_INSTANCE.toBuilder(); }
  public static Builder newBuilder(com.drawgame.game.grpc.generated.GuessResponse prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  @java.lang.Override
  public Builder toBuilder() { return this == DEFAULT_INSTANCE ? new Builder() : new Builder().mergeFrom(this); }

  @java.lang.Override
  protected Builder newBuilderForType(com.google.protobuf.GeneratedMessage.BuilderParent parent) {
    return new Builder(parent);
  }

  public static final class Builder extends
      com.google.protobuf.GeneratedMessage.Builder<Builder> implements
      com.drawgame.game.grpc.generated.GuessResponseOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_GuessResponse_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_GuessResponse_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.drawgame.game.grpc.generated.GuessResponse.class, com.drawgame.game.grpc.generated.GuessResponse.Builder.class);
    }

    private Builder() {}
    private Builder(com.google.protobuf.GeneratedMessage.BuilderParent parent) { super(parent); }

    @java.lang.Override
    public Builder clear() {
      super.clear();
      roomId_ = "";
      playerId_ = "";
      guessStatus_ = "";
      scoreAwarded_ = 0;
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_GuessResponse_descriptor;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.GuessResponse getDefaultInstanceForType() {
      return com.drawgame.game.grpc.generated.GuessResponse.getDefaultInstance();
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.GuessResponse build() {
      com.drawgame.game.grpc.generated.GuessResponse result = buildPartial();
      if (!result.isInitialized()) throw newUninitializedMessageException(result);
      return result;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.GuessResponse buildPartial() {
      com.drawgame.game.grpc.generated.GuessResponse result = new com.drawgame.game.grpc.generated.GuessResponse(this);
      result.roomId_ = roomId_;
      result.playerId_ = playerId_;
      result.guessStatus_ = guessStatus_;
      result.scoreAwarded_ = scoreAwarded_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.drawgame.game.grpc.generated.GuessResponse) {
        return mergeFrom((com.drawgame.game.grpc.generated.GuessResponse)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.drawgame.game.grpc.generated.GuessResponse other) {
      if (other == com.drawgame.game.grpc.generated.GuessResponse.getDefaultInstance()) return this;
      if (!other.getRoomId().isEmpty()) { roomId_ = other.roomId_; onChanged(); }
      if (!other.getPlayerId().isEmpty()) { playerId_ = other.playerId_; onChanged(); }
      if (!other.getGuessStatus().isEmpty()) { guessStatus_ = other.guessStatus_; onChanged(); }
      if (other.getScoreAwarded() != 0) { setScoreAwarded(other.getScoreAwarded()); }
      this.mergeUnknownFields(other.getUnknownFields());
      onChanged();
      return this;
    }

    @java.lang.Override
    public final boolean isInitialized() { return true; }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.CodedInputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0: done = true; break;
            case 10: roomId_ = input.readStringRequireUtf8(); break;
            case 18: playerId_ = input.readStringRequireUtf8(); break;
            case 26: guessStatus_ = input.readStringRequireUtf8(); break;
            case 32: scoreAwarded_ = input.readInt32(); break;
            default: if (!super.parseUnknownField(input, extensionRegistry, tag)) done = true; break;
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.unwrapIOException();
      } finally {
        onChanged();
      }
      return this;
    }

    private java.lang.Object roomId_ = "";
    public java.lang.String getRoomId() {
      java.lang.Object ref = roomId_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        roomId_ = s;
        return s;
      } else return (java.lang.String) ref;
    }
    public com.google.protobuf.ByteString getRoomIdBytes() {
      java.lang.Object ref = roomId_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        roomId_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public Builder setRoomId(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      roomId_ = value;
      onChanged();
      return this;
    }

    private java.lang.Object playerId_ = "";
    public java.lang.String getPlayerId() {
      java.lang.Object ref = playerId_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        playerId_ = s;
        return s;
      } else return (java.lang.String) ref;
    }
    public com.google.protobuf.ByteString getPlayerIdBytes() {
      java.lang.Object ref = playerId_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        playerId_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public Builder setPlayerId(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      playerId_ = value;
      onChanged();
      return this;
    }

    private java.lang.Object guessStatus_ = "";
    public java.lang.String getGuessStatus() {
      java.lang.Object ref = guessStatus_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        guessStatus_ = s;
        return s;
      } else return (java.lang.String) ref;
    }
    public com.google.protobuf.ByteString getGuessStatusBytes() {
      java.lang.Object ref = guessStatus_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        guessStatus_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public Builder setGuessStatus(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      guessStatus_ = value;
      onChanged();
      return this;
    }

    private int scoreAwarded_;
    public int getScoreAwarded() { return scoreAwarded_; }
    public Builder setScoreAwarded(int value) {
      scoreAwarded_ = value;
      onChanged();
      return this;
    }
  }

  private static final com.drawgame.game.grpc.generated.GuessResponse DEFAULT_INSTANCE;
  static { DEFAULT_INSTANCE = new com.drawgame.game.grpc.generated.GuessResponse(); }

  public static com.drawgame.game.grpc.generated.GuessResponse getDefaultInstance() { return DEFAULT_INSTANCE; }

  private static final com.google.protobuf.Parser<GuessResponse> PARSER = new com.google.protobuf.AbstractParser<GuessResponse>() {
    @java.lang.Override
    public GuessResponse parsePartialFrom(com.google.protobuf.CodedInputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
      Builder builder = newBuilder();
      try { builder.mergeFrom(input, extensionRegistry); }
      catch (com.google.protobuf.InvalidProtocolBufferException e) { throw e.setUnfinishedMessage(builder.buildPartial()); }
      catch (java.io.IOException e) { throw new com.google.protobuf.InvalidProtocolBufferException(e).setUnfinishedMessage(builder.buildPartial()); }
      return builder.buildPartial();
    }
  };

  public static com.google.protobuf.Parser<GuessResponse> parser() { return PARSER; }

  @java.lang.Override
  public com.google.protobuf.Parser<GuessResponse> getParserForType() { return PARSER; }

  @java.lang.Override
  public com.drawgame.game.grpc.generated.GuessResponse getDefaultInstanceForType() { return DEFAULT_INSTANCE; }
}
