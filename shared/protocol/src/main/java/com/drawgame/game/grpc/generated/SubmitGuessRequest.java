package com.drawgame.game.grpc.generated;

public final class SubmitGuessRequest extends
    com.google.protobuf.GeneratedMessage implements
    SubmitGuessRequestOrBuilder {
private static final long serialVersionUID = 0L;

  private SubmitGuessRequest(com.google.protobuf.GeneratedMessage.Builder<?> builder) { super(builder); }
  private SubmitGuessRequest() {
    roomId_ = "";
    playerId_ = "";
    guess_ = "";
  }

  public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_SubmitGuessRequest_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_SubmitGuessRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.drawgame.game.grpc.generated.SubmitGuessRequest.class, com.drawgame.game.grpc.generated.SubmitGuessRequest.Builder.class);
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

  private volatile java.lang.Object guess_ = "";
  public java.lang.String getGuess() {
    java.lang.Object ref = guess_;
    if (ref instanceof java.lang.String) return (java.lang.String) ref;
    else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      guess_ = s;
      return s;
    }
  }
  public com.google.protobuf.ByteString getGuessBytes() {
    java.lang.Object ref = guess_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      guess_ = b;
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
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(playerId_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 2, playerId_);
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(guess_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 3, guess_);
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
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(guess_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(3, guess_);
    }
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() { return DEFAULT_INSTANCE.toBuilder(); }
  public static Builder newBuilder(com.drawgame.game.grpc.generated.SubmitGuessRequest prototype) {
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
      com.drawgame.game.grpc.generated.SubmitGuessRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_SubmitGuessRequest_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_SubmitGuessRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.drawgame.game.grpc.generated.SubmitGuessRequest.class, com.drawgame.game.grpc.generated.SubmitGuessRequest.Builder.class);
    }

    private Builder() {}
    private Builder(com.google.protobuf.GeneratedMessage.BuilderParent parent) { super(parent); }

    @java.lang.Override
    public Builder clear() {
      super.clear();
      roomId_ = "";
      playerId_ = "";
      guess_ = "";
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_SubmitGuessRequest_descriptor;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.SubmitGuessRequest getDefaultInstanceForType() {
      return com.drawgame.game.grpc.generated.SubmitGuessRequest.getDefaultInstance();
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.SubmitGuessRequest build() {
      com.drawgame.game.grpc.generated.SubmitGuessRequest result = buildPartial();
      if (!result.isInitialized()) throw newUninitializedMessageException(result);
      return result;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.SubmitGuessRequest buildPartial() {
      com.drawgame.game.grpc.generated.SubmitGuessRequest result = new com.drawgame.game.grpc.generated.SubmitGuessRequest(this);
      result.roomId_ = roomId_;
      result.playerId_ = playerId_;
      result.guess_ = guess_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.drawgame.game.grpc.generated.SubmitGuessRequest) {
        return mergeFrom((com.drawgame.game.grpc.generated.SubmitGuessRequest)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.drawgame.game.grpc.generated.SubmitGuessRequest other) {
      if (other == com.drawgame.game.grpc.generated.SubmitGuessRequest.getDefaultInstance()) return this;
      if (!other.getRoomId().isEmpty()) { roomId_ = other.roomId_; onChanged(); }
      if (!other.getPlayerId().isEmpty()) { playerId_ = other.playerId_; onChanged(); }
      if (!other.getGuess().isEmpty()) { guess_ = other.guess_; onChanged(); }
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
            case 26: guess_ = input.readStringRequireUtf8(); break;
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

    private java.lang.Object guess_ = "";
    public java.lang.String getGuess() {
      java.lang.Object ref = guess_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        guess_ = s;
        return s;
      } else return (java.lang.String) ref;
    }
    public com.google.protobuf.ByteString getGuessBytes() {
      java.lang.Object ref = guess_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        guess_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public Builder setGuess(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      guess_ = value;
      onChanged();
      return this;
    }
  }

  private static final com.drawgame.game.grpc.generated.SubmitGuessRequest DEFAULT_INSTANCE;
  static { DEFAULT_INSTANCE = new com.drawgame.game.grpc.generated.SubmitGuessRequest(); }

  public static com.drawgame.game.grpc.generated.SubmitGuessRequest getDefaultInstance() { return DEFAULT_INSTANCE; }

  private static final com.google.protobuf.Parser<SubmitGuessRequest> PARSER = new com.google.protobuf.AbstractParser<SubmitGuessRequest>() {
    @java.lang.Override
    public SubmitGuessRequest parsePartialFrom(com.google.protobuf.CodedInputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
      Builder builder = newBuilder();
      try { builder.mergeFrom(input, extensionRegistry); }
      catch (com.google.protobuf.InvalidProtocolBufferException e) { throw e.setUnfinishedMessage(builder.buildPartial()); }
      catch (java.io.IOException e) { throw new com.google.protobuf.InvalidProtocolBufferException(e).setUnfinishedMessage(builder.buildPartial()); }
      return builder.buildPartial();
    }
  };

  public static com.google.protobuf.Parser<SubmitGuessRequest> parser() { return PARSER; }

  @java.lang.Override
  public com.google.protobuf.Parser<SubmitGuessRequest> getParserForType() { return PARSER; }

  @java.lang.Override
  public com.drawgame.game.grpc.generated.SubmitGuessRequest getDefaultInstanceForType() { return DEFAULT_INSTANCE; }
}
