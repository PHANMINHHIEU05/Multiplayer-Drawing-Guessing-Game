package com.drawgame.game.grpc.generated;

public final class PlayerScoreMessage extends
    com.google.protobuf.GeneratedMessage implements
    PlayerScoreMessageOrBuilder {
private static final long serialVersionUID = 0L;

  private PlayerScoreMessage(com.google.protobuf.GeneratedMessage.Builder<?> builder) { super(builder); }
  private PlayerScoreMessage() {
    playerId_ = "";
    username_ = "";
  }

  public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_PlayerScoreMessage_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_PlayerScoreMessage_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.drawgame.game.grpc.generated.PlayerScoreMessage.class, com.drawgame.game.grpc.generated.PlayerScoreMessage.Builder.class);
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

  private volatile java.lang.Object username_ = "";
  public java.lang.String getUsername() {
    java.lang.Object ref = username_;
    if (ref instanceof java.lang.String) return (java.lang.String) ref;
    else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      username_ = s;
      return s;
    }
  }
  public com.google.protobuf.ByteString getUsernameBytes() {
    java.lang.Object ref = username_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      username_ = b;
      return b;
    } else return (com.google.protobuf.ByteString) ref;
  }

  private int score_;
  public int getScore() { return score_; }

  private boolean hasGuessed_;
  public boolean getHasGuessed() { return hasGuessed_; }

  @java.lang.Override
  public final boolean isInitialized() { return true; }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output) throws java.io.IOException {
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(playerId_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 1, playerId_);
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(username_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 2, username_);
    }
    if (score_ != 0) {
      output.writeInt32(3, score_);
    }
    if (hasGuessed_) {
      output.writeBool(4, hasGuessed_);
    }
    getUnknownFields().writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;
    size = 0;
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(playerId_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(1, playerId_);
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(username_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(2, username_);
    }
    if (score_ != 0) {
      size += com.google.protobuf.CodedOutputStream.computeInt32Size(3, score_);
    }
    if (hasGuessed_) {
      size += com.google.protobuf.CodedOutputStream.computeBoolSize(4, hasGuessed_);
    }
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() { return DEFAULT_INSTANCE.toBuilder(); }
  public static Builder newBuilder(com.drawgame.game.grpc.generated.PlayerScoreMessage prototype) {
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
      com.drawgame.game.grpc.generated.PlayerScoreMessageOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_PlayerScoreMessage_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_PlayerScoreMessage_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.drawgame.game.grpc.generated.PlayerScoreMessage.class, com.drawgame.game.grpc.generated.PlayerScoreMessage.Builder.class);
    }

    private Builder() {}
    private Builder(com.google.protobuf.GeneratedMessage.BuilderParent parent) { super(parent); }

    @java.lang.Override
    public Builder clear() {
      super.clear();
      playerId_ = "";
      username_ = "";
      score_ = 0;
      hasGuessed_ = false;
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_PlayerScoreMessage_descriptor;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.PlayerScoreMessage getDefaultInstanceForType() {
      return com.drawgame.game.grpc.generated.PlayerScoreMessage.getDefaultInstance();
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.PlayerScoreMessage build() {
      com.drawgame.game.grpc.generated.PlayerScoreMessage result = buildPartial();
      if (!result.isInitialized()) throw newUninitializedMessageException(result);
      return result;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.PlayerScoreMessage buildPartial() {
      com.drawgame.game.grpc.generated.PlayerScoreMessage result = new com.drawgame.game.grpc.generated.PlayerScoreMessage(this);
      result.playerId_ = playerId_;
      result.username_ = username_;
      result.score_ = score_;
      result.hasGuessed_ = hasGuessed_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.drawgame.game.grpc.generated.PlayerScoreMessage) {
        return mergeFrom((com.drawgame.game.grpc.generated.PlayerScoreMessage)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.drawgame.game.grpc.generated.PlayerScoreMessage other) {
      if (other == com.drawgame.game.grpc.generated.PlayerScoreMessage.getDefaultInstance()) return this;
      if (!other.getPlayerId().isEmpty()) { playerId_ = other.playerId_; onChanged(); }
      if (!other.getUsername().isEmpty()) { username_ = other.username_; onChanged(); }
      if (other.getScore() != 0) { setScore(other.getScore()); }
      if (other.getHasGuessed()) { setHasGuessed(other.getHasGuessed()); }
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
            case 10: playerId_ = input.readStringRequireUtf8(); break;
            case 18: username_ = input.readStringRequireUtf8(); break;
            case 24: score_ = input.readInt32(); break;
            case 32: hasGuessed_ = input.readBool(); break;
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

    private java.lang.Object username_ = "";
    public java.lang.String getUsername() {
      java.lang.Object ref = username_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        username_ = s;
        return s;
      } else return (java.lang.String) ref;
    }
    public com.google.protobuf.ByteString getUsernameBytes() {
      java.lang.Object ref = username_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        username_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public Builder setUsername(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      username_ = value;
      onChanged();
      return this;
    }

    private int score_;
    public int getScore() { return score_; }
    public Builder setScore(int value) {
      score_ = value;
      onChanged();
      return this;
    }

    private boolean hasGuessed_;
    public boolean getHasGuessed() { return hasGuessed_; }
    public Builder setHasGuessed(boolean value) {
      hasGuessed_ = value;
      onChanged();
      return this;
    }
  }

  private static final com.drawgame.game.grpc.generated.PlayerScoreMessage DEFAULT_INSTANCE;
  static { DEFAULT_INSTANCE = new com.drawgame.game.grpc.generated.PlayerScoreMessage(); }

  public static com.drawgame.game.grpc.generated.PlayerScoreMessage getDefaultInstance() { return DEFAULT_INSTANCE; }

  private static final com.google.protobuf.Parser<PlayerScoreMessage> PARSER = new com.google.protobuf.AbstractParser<PlayerScoreMessage>() {
    @java.lang.Override
    public PlayerScoreMessage parsePartialFrom(com.google.protobuf.CodedInputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
      Builder builder = newBuilder();
      try { builder.mergeFrom(input, extensionRegistry); }
      catch (com.google.protobuf.InvalidProtocolBufferException e) { throw e.setUnfinishedMessage(builder.buildPartial()); }
      catch (java.io.IOException e) { throw new com.google.protobuf.InvalidProtocolBufferException(e).setUnfinishedMessage(builder.buildPartial()); }
      return builder.buildPartial();
    }
  };

  public static com.google.protobuf.Parser<PlayerScoreMessage> parser() { return PARSER; }

  @java.lang.Override
  public com.google.protobuf.Parser<PlayerScoreMessage> getParserForType() { return PARSER; }

  @java.lang.Override
  public com.drawgame.game.grpc.generated.PlayerScoreMessage getDefaultInstanceForType() { return DEFAULT_INSTANCE; }
}
