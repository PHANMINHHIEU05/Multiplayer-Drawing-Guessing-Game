package com.drawgame.game.grpc.generated;

public final class GameStateResponse extends
    com.google.protobuf.GeneratedMessage implements
    GameStateResponseOrBuilder {
private static final long serialVersionUID = 0L;

  private GameStateResponse(com.google.protobuf.GeneratedMessage.Builder<?> builder) { super(builder); }
  private GameStateResponse() {
    roomId_ = "";
    status_ = "";
    drawerId_ = "";
    hint_ = "";
    secretWord_ = "";
    scores_ = java.util.Collections.emptyList();
  }

  public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_GameStateResponse_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_GameStateResponse_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.drawgame.game.grpc.generated.GameStateResponse.class, com.drawgame.game.grpc.generated.GameStateResponse.Builder.class);
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

  private volatile java.lang.Object status_ = "";
  public java.lang.String getStatus() {
    java.lang.Object ref = status_;
    if (ref instanceof java.lang.String) return (java.lang.String) ref;
    else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      status_ = s;
      return s;
    }
  }
  public com.google.protobuf.ByteString getStatusBytes() {
    java.lang.Object ref = status_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      status_ = b;
      return b;
    } else return (com.google.protobuf.ByteString) ref;
  }

  private int currentRound_;
  public int getCurrentRound() { return currentRound_; }

  private int totalRounds_;
  public int getTotalRounds() { return totalRounds_; }

  private volatile java.lang.Object drawerId_ = "";
  public java.lang.String getDrawerId() {
    java.lang.Object ref = drawerId_;
    if (ref instanceof java.lang.String) return (java.lang.String) ref;
    else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      drawerId_ = s;
      return s;
    }
  }
  public com.google.protobuf.ByteString getDrawerIdBytes() {
    java.lang.Object ref = drawerId_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      drawerId_ = b;
      return b;
    } else return (com.google.protobuf.ByteString) ref;
  }

  private long roundStartedAt_;
  public long getRoundStartedAt() { return roundStartedAt_; }

  private long roundEndsAt_;
  public long getRoundEndsAt() { return roundEndsAt_; }

  private java.util.List<com.drawgame.game.grpc.generated.PlayerScoreMessage> scores_;
  public java.util.List<com.drawgame.game.grpc.generated.PlayerScoreMessage> getScoresList() { return scores_; }
  public int getScoresCount() { return scores_.size(); }
  public com.drawgame.game.grpc.generated.PlayerScoreMessage getScores(int index) { return scores_.get(index); }

  private volatile java.lang.Object hint_ = "";
  public java.lang.String getHint() {
    java.lang.Object ref = hint_;
    if (ref instanceof java.lang.String) return (java.lang.String) ref;
    else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      hint_ = s;
      return s;
    }
  }
  public com.google.protobuf.ByteString getHintBytes() {
    java.lang.Object ref = hint_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      hint_ = b;
      return b;
    } else return (com.google.protobuf.ByteString) ref;
  }

  private volatile java.lang.Object secretWord_ = "";
  public java.lang.String getSecretWord() {
    java.lang.Object ref = secretWord_;
    if (ref instanceof java.lang.String) return (java.lang.String) ref;
    else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      secretWord_ = s;
      return s;
    }
  }
  public com.google.protobuf.ByteString getSecretWordBytes() {
    java.lang.Object ref = secretWord_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      secretWord_ = b;
      return b;
    } else return (com.google.protobuf.ByteString) ref;
  }

  @java.lang.Override
  public final boolean isInitialized() { return true; }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output) throws java.io.IOException {
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(roomId_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 1, roomId_);
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(status_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 2, status_);
    }
    if (currentRound_ != 0) { output.writeInt32(3, currentRound_); }
    if (totalRounds_ != 0) { output.writeInt32(4, totalRounds_); }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(drawerId_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 5, drawerId_);
    }
    if (roundStartedAt_ != 0L) { output.writeInt64(6, roundStartedAt_); }
    if (roundEndsAt_ != 0L) { output.writeInt64(7, roundEndsAt_); }
    for (int i = 0; i < scores_.size(); i++) {
      output.writeMessage(8, scores_.get(i));
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(hint_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 9, hint_);
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(secretWord_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 10, secretWord_);
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
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(status_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(2, status_);
    }
    if (currentRound_ != 0) { size += com.google.protobuf.CodedOutputStream.computeInt32Size(3, currentRound_); }
    if (totalRounds_ != 0) { size += com.google.protobuf.CodedOutputStream.computeInt32Size(4, totalRounds_); }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(drawerId_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(5, drawerId_);
    }
    if (roundStartedAt_ != 0L) { size += com.google.protobuf.CodedOutputStream.computeInt64Size(6, roundStartedAt_); }
    if (roundEndsAt_ != 0L) { size += com.google.protobuf.CodedOutputStream.computeInt64Size(7, roundEndsAt_); }
    for (int i = 0; i < scores_.size(); i++) {
      size += com.google.protobuf.CodedOutputStream.computeMessageSize(8, scores_.get(i));
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(hint_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(9, hint_);
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(secretWord_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(10, secretWord_);
    }
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() { return DEFAULT_INSTANCE.toBuilder(); }
  public static Builder newBuilder(com.drawgame.game.grpc.generated.GameStateResponse prototype) {
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
      com.drawgame.game.grpc.generated.GameStateResponseOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_GameStateResponse_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_GameStateResponse_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.drawgame.game.grpc.generated.GameStateResponse.class, com.drawgame.game.grpc.generated.GameStateResponse.Builder.class);
    }

    private Builder() {}
    private Builder(com.google.protobuf.GeneratedMessage.BuilderParent parent) { super(parent); }

    @java.lang.Override
    public Builder clear() {
      super.clear();
      roomId_ = "";
      status_ = "";
      currentRound_ = 0;
      totalRounds_ = 0;
      drawerId_ = "";
      roundStartedAt_ = 0L;
      roundEndsAt_ = 0L;
      if (scoresBuilder_ == null) { scores_ = java.util.Collections.emptyList(); bitField0_ &= ~0x00000080; } else { scoresBuilder_.clear(); }
      hint_ = "";
      secretWord_ = "";
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_GameStateResponse_descriptor;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.GameStateResponse getDefaultInstanceForType() {
      return com.drawgame.game.grpc.generated.GameStateResponse.getDefaultInstance();
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.GameStateResponse build() {
      com.drawgame.game.grpc.generated.GameStateResponse result = buildPartial();
      if (!result.isInitialized()) throw newUninitializedMessageException(result);
      return result;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.GameStateResponse buildPartial() {
      com.drawgame.game.grpc.generated.GameStateResponse result = new com.drawgame.game.grpc.generated.GameStateResponse(this);
      result.roomId_ = roomId_;
      result.status_ = status_;
      result.currentRound_ = currentRound_;
      result.totalRounds_ = totalRounds_;
      result.drawerId_ = drawerId_;
      result.roundStartedAt_ = roundStartedAt_;
      result.roundEndsAt_ = roundEndsAt_;
      if (scoresBuilder_ == null) {
        if ((bitField0_ & 0x00000080) != 0) { scores_ = java.util.Collections.unmodifiableList(scores_); bitField0_ &= ~0x00000080; }
        result.scores_ = scores_;
      } else { result.scores_ = scoresBuilder_.build(); }
      result.hint_ = hint_;
      result.secretWord_ = secretWord_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.drawgame.game.grpc.generated.GameStateResponse) {
        return mergeFrom((com.drawgame.game.grpc.generated.GameStateResponse)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.drawgame.game.grpc.generated.GameStateResponse other) {
      if (other == com.drawgame.game.grpc.generated.GameStateResponse.getDefaultInstance()) return this;
      if (!other.getRoomId().isEmpty()) { roomId_ = other.roomId_; onChanged(); }
      if (!other.getStatus().isEmpty()) { status_ = other.status_; onChanged(); }
      if (other.getCurrentRound() != 0) { setCurrentRound(other.getCurrentRound()); }
      if (other.getTotalRounds() != 0) { setTotalRounds(other.getTotalRounds()); }
      if (!other.getDrawerId().isEmpty()) { drawerId_ = other.drawerId_; onChanged(); }
      if (other.getRoundStartedAt() != 0L) { setRoundStartedAt(other.getRoundStartedAt()); }
      if (other.getRoundEndsAt() != 0L) { setRoundEndsAt(other.getRoundEndsAt()); }
      if (scoresBuilder_ == null) {
        if (!other.scores_.isEmpty()) {
          if (scores_.isEmpty()) { scores_ = other.scores_; bitField0_ &= ~0x00000080; }
          else { ensureScoresIsMutable(); scores_.addAll(other.scores_); }
          onChanged();
        }
      } else {
        if (!other.scores_.isEmpty()) {
          if (scoresBuilder_.isEmpty()) { scoresBuilder_.dispose(); scoresBuilder_ = null; scores_ = other.scores_; bitField0_ &= ~0x00000080; scoresBuilder_ = java.lang.Boolean.TRUE.equals(null) ? getScoresFieldBuilder() : null; }
          else { scoresBuilder_.addAllMessages(other.scores_); }
        }
      }
      if (!other.getHint().isEmpty()) { hint_ = other.hint_; onChanged(); }
      if (!other.getSecretWord().isEmpty()) { secretWord_ = other.secretWord_; onChanged(); }
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
            case 18: status_ = input.readStringRequireUtf8(); break;
            case 24: currentRound_ = input.readInt32(); break;
            case 32: totalRounds_ = input.readInt32(); break;
            case 42: drawerId_ = input.readStringRequireUtf8(); break;
            case 48: roundStartedAt_ = input.readInt64(); break;
            case 56: roundEndsAt_ = input.readInt64(); break;
            case 66: {
              com.drawgame.game.grpc.generated.PlayerScoreMessage m = input.readMessage(com.drawgame.game.grpc.generated.PlayerScoreMessage.parser(), extensionRegistry);
              if (scoresBuilder_ == null) { ensureScoresIsMutable(); scores_.add(m); } else { scoresBuilder_.addMessage(m); }
              break;
            }
            case 74: hint_ = input.readStringRequireUtf8(); break;
            case 82: secretWord_ = input.readStringRequireUtf8(); break;
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

    private int bitField0_;

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

    private java.lang.Object status_ = "";
    public java.lang.String getStatus() {
      java.lang.Object ref = status_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        status_ = s;
        return s;
      } else return (java.lang.String) ref;
    }
    public com.google.protobuf.ByteString getStatusBytes() {
      java.lang.Object ref = status_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        status_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public Builder setStatus(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      status_ = value;
      onChanged();
      return this;
    }

    private int currentRound_;
    public int getCurrentRound() { return currentRound_; }
    public Builder setCurrentRound(int value) { currentRound_ = value; onChanged(); return this; }

    private int totalRounds_;
    public int getTotalRounds() { return totalRounds_; }
    public Builder setTotalRounds(int value) { totalRounds_ = value; onChanged(); return this; }

    private java.lang.Object drawerId_ = "";
    public java.lang.String getDrawerId() {
      java.lang.Object ref = drawerId_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        drawerId_ = s;
        return s;
      } else return (java.lang.String) ref;
    }
    public com.google.protobuf.ByteString getDrawerIdBytes() {
      java.lang.Object ref = drawerId_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        drawerId_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public Builder setDrawerId(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      drawerId_ = value;
      onChanged();
      return this;
    }

    private long roundStartedAt_;
    public long getRoundStartedAt() { return roundStartedAt_; }
    public Builder setRoundStartedAt(long value) { roundStartedAt_ = value; onChanged(); return this; }

    private long roundEndsAt_;
    public long getRoundEndsAt() { return roundEndsAt_; }
    public Builder setRoundEndsAt(long value) { roundEndsAt_ = value; onChanged(); return this; }

    private java.util.List<com.drawgame.game.grpc.generated.PlayerScoreMessage> scores_ = java.util.Collections.emptyList();
    private void ensureScoresIsMutable() {
      if ((bitField0_ & 0x00000080) == 0) {
        scores_ = new java.util.ArrayList<com.drawgame.game.grpc.generated.PlayerScoreMessage>(scores_);
        bitField0_ |= 0x00000080;
      }
    }
    public java.util.List<com.drawgame.game.grpc.generated.PlayerScoreMessage> getScoresList() {
      if (scoresBuilder_ == null) return java.util.Collections.unmodifiableList(scores_);
      else return scoresBuilder_.getMessageList();
    }
    public int getScoresCount() {
      if (scoresBuilder_ == null) return scores_.size();
      else return scoresBuilder_.getCount();
    }
    public com.drawgame.game.grpc.generated.PlayerScoreMessage getScores(int index) {
      if (scoresBuilder_ == null) return scores_.get(index);
      else return scoresBuilder_.getMessage(index);
    }
    public Builder addScores(com.drawgame.game.grpc.generated.PlayerScoreMessage value) {
      if (scoresBuilder_ == null) {
        if (value == null) throw new NullPointerException();
        ensureScoresIsMutable();
        scores_.add(value);
        onChanged();
      } else { scoresBuilder_.addMessage(value); }
      return this;
    }

    private com.google.protobuf.RepeatedFieldBuilder<com.drawgame.game.grpc.generated.PlayerScoreMessage, com.drawgame.game.grpc.generated.PlayerScoreMessage.Builder, com.drawgame.game.grpc.generated.PlayerScoreMessageOrBuilder> scoresBuilder_;
    private com.google.protobuf.RepeatedFieldBuilder<com.drawgame.game.grpc.generated.PlayerScoreMessage, com.drawgame.game.grpc.generated.PlayerScoreMessage.Builder, com.drawgame.game.grpc.generated.PlayerScoreMessageOrBuilder> getScoresFieldBuilder() {
      if (scoresBuilder_ == null) {
        scoresBuilder_ = new com.google.protobuf.RepeatedFieldBuilder<com.drawgame.game.grpc.generated.PlayerScoreMessage, com.drawgame.game.grpc.generated.PlayerScoreMessage.Builder, com.drawgame.game.grpc.generated.PlayerScoreMessageOrBuilder>(
            scores_, ((bitField0_ & 0x00000080) != 0), getParentForChildren(), isClean());
        scores_ = null;
      }
      return scoresBuilder_;
    }

    private java.lang.Object hint_ = "";
    public java.lang.String getHint() {
      java.lang.Object ref = hint_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        hint_ = s;
        return s;
      } else return (java.lang.String) ref;
    }
    public com.google.protobuf.ByteString getHintBytes() {
      java.lang.Object ref = hint_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        hint_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public Builder setHint(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      hint_ = value;
      onChanged();
      return this;
    }

    private java.lang.Object secretWord_ = "";
    public java.lang.String getSecretWord() {
      java.lang.Object ref = secretWord_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        secretWord_ = s;
        return s;
      } else return (java.lang.String) ref;
    }
    public com.google.protobuf.ByteString getSecretWordBytes() {
      java.lang.Object ref = secretWord_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        secretWord_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public Builder setSecretWord(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      secretWord_ = value;
      onChanged();
      return this;
    }
  }

  private static final com.drawgame.game.grpc.generated.GameStateResponse DEFAULT_INSTANCE;
  static { DEFAULT_INSTANCE = new com.drawgame.game.grpc.generated.GameStateResponse(); }

  public static com.drawgame.game.grpc.generated.GameStateResponse getDefaultInstance() { return DEFAULT_INSTANCE; }

  private static final com.google.protobuf.Parser<GameStateResponse> PARSER = new com.google.protobuf.AbstractParser<GameStateResponse>() {
    @java.lang.Override
    public GameStateResponse parsePartialFrom(com.google.protobuf.CodedInputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
      Builder builder = newBuilder();
      try { builder.mergeFrom(input, extensionRegistry); }
      catch (com.google.protobuf.InvalidProtocolBufferException e) { throw e.setUnfinishedMessage(builder.buildPartial()); }
      catch (java.io.IOException e) { throw new com.google.protobuf.InvalidProtocolBufferException(e).setUnfinishedMessage(builder.buildPartial()); }
      return builder.buildPartial();
    }
  };

  public static com.google.protobuf.Parser<GameStateResponse> parser() { return PARSER; }

  @java.lang.Override
  public com.google.protobuf.Parser<GameStateResponse> getParserForType() { return PARSER; }

  @java.lang.Override
  public com.drawgame.game.grpc.generated.GameStateResponse getDefaultInstanceForType() { return DEFAULT_INSTANCE; }
}
