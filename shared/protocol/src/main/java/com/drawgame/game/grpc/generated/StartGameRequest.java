package com.drawgame.game.grpc.generated;

public final class StartGameRequest extends
    com.google.protobuf.GeneratedMessage implements
    StartGameRequestOrBuilder {
private static final long serialVersionUID = 0L;

  private StartGameRequest(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
    super(builder);
  }
  private StartGameRequest() {
    roomId_ = "";
    requesterPlayerId_ = "";
  }

  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_StartGameRequest_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_StartGameRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.drawgame.game.grpc.generated.StartGameRequest.class, com.drawgame.game.grpc.generated.StartGameRequest.Builder.class);
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

  private volatile java.lang.Object requesterPlayerId_ = "";
  public java.lang.String getRequesterPlayerId() {
    java.lang.Object ref = requesterPlayerId_;
    if (ref instanceof java.lang.String) return (java.lang.String) ref;
    else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      requesterPlayerId_ = s;
      return s;
    }
  }
  public com.google.protobuf.ByteString getRequesterPlayerIdBytes() {
    java.lang.Object ref = requesterPlayerId_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      requesterPlayerId_ = b;
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
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(requesterPlayerId_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 2, requesterPlayerId_);
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
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(requesterPlayerId_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(2, requesterPlayerId_);
    }
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() { return DEFAULT_INSTANCE.toBuilder(); }
  public static Builder newBuilder(com.drawgame.game.grpc.generated.StartGameRequest prototype) {
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
      com.drawgame.game.grpc.generated.StartGameRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_StartGameRequest_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_StartGameRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.drawgame.game.grpc.generated.StartGameRequest.class, com.drawgame.game.grpc.generated.StartGameRequest.Builder.class);
    }

    private Builder() {}
    private Builder(com.google.protobuf.GeneratedMessage.BuilderParent parent) { super(parent); }

    @java.lang.Override
    public Builder clear() {
      super.clear();
      roomId_ = "";
      requesterPlayerId_ = "";
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_StartGameRequest_descriptor;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.StartGameRequest getDefaultInstanceForType() {
      return com.drawgame.game.grpc.generated.StartGameRequest.getDefaultInstance();
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.StartGameRequest build() {
      com.drawgame.game.grpc.generated.StartGameRequest result = buildPartial();
      if (!result.isInitialized()) throw newUninitializedMessageException(result);
      return result;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.StartGameRequest buildPartial() {
      com.drawgame.game.grpc.generated.StartGameRequest result = new com.drawgame.game.grpc.generated.StartGameRequest(this);
      result.roomId_ = roomId_;
      result.requesterPlayerId_ = requesterPlayerId_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.drawgame.game.grpc.generated.StartGameRequest) {
        return mergeFrom((com.drawgame.game.grpc.generated.StartGameRequest)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.drawgame.game.grpc.generated.StartGameRequest other) {
      if (other == com.drawgame.game.grpc.generated.StartGameRequest.getDefaultInstance()) return this;
      if (!other.getRoomId().isEmpty()) { roomId_ = other.roomId_; onChanged(); }
      if (!other.getRequesterPlayerId().isEmpty()) { requesterPlayerId_ = other.requesterPlayerId_; onChanged(); }
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
            case 18: requesterPlayerId_ = input.readStringRequireUtf8(); break;
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

    private java.lang.Object requesterPlayerId_ = "";
    public java.lang.String getRequesterPlayerId() {
      java.lang.Object ref = requesterPlayerId_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        requesterPlayerId_ = s;
        return s;
      } else return (java.lang.String) ref;
    }
    public com.google.protobuf.ByteString getRequesterPlayerIdBytes() {
      java.lang.Object ref = requesterPlayerId_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        requesterPlayerId_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public Builder setRequesterPlayerId(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      requesterPlayerId_ = value;
      onChanged();
      return this;
    }
  }

  private static final com.drawgame.game.grpc.generated.StartGameRequest DEFAULT_INSTANCE;
  static { DEFAULT_INSTANCE = new com.drawgame.game.grpc.generated.StartGameRequest(); }

  public static com.drawgame.game.grpc.generated.StartGameRequest getDefaultInstance() { return DEFAULT_INSTANCE; }

  private static final com.google.protobuf.Parser<StartGameRequest> PARSER = new com.google.protobuf.AbstractParser<StartGameRequest>() {
    @java.lang.Override
    public StartGameRequest parsePartialFrom(com.google.protobuf.CodedInputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
      Builder builder = newBuilder();
      try { builder.mergeFrom(input, extensionRegistry); }
      catch (com.google.protobuf.InvalidProtocolBufferException e) { throw e.setUnfinishedMessage(builder.buildPartial()); }
      catch (java.io.IOException e) { throw new com.google.protobuf.InvalidProtocolBufferException(e).setUnfinishedMessage(builder.buildPartial()); }
      return builder.buildPartial();
    }
  };

  public static com.google.protobuf.Parser<StartGameRequest> parser() { return PARSER; }

  @java.lang.Override
  public com.google.protobuf.Parser<StartGameRequest> getParserForType() { return PARSER; }

  @java.lang.Override
  public com.drawgame.game.grpc.generated.StartGameRequest getDefaultInstanceForType() { return DEFAULT_INSTANCE; }
}
