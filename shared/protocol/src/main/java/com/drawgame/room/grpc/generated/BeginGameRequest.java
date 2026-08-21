package com.drawgame.room.grpc.generated;

public final class BeginGameRequest extends
    com.google.protobuf.GeneratedMessage implements
    BeginGameRequestOrBuilder {
private static final long serialVersionUID = 0L;

  private BeginGameRequest(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
    super(builder);
  }
  private BeginGameRequest() {
    roomId_ = "";
    playerId_ = "";
  }

  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.drawgame.room.grpc.generated.Room.internal_static_room_BeginGameRequest_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.drawgame.room.grpc.generated.Room.internal_static_room_BeginGameRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.drawgame.room.grpc.generated.BeginGameRequest.class, com.drawgame.room.grpc.generated.BeginGameRequest.Builder.class);
  }

  private volatile java.lang.Object roomId_ = "";
  public java.lang.String getRoomId() {
    java.lang.Object ref = roomId_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
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
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
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
  public java.lang.String getRequesterPlayerId() { return getPlayerId(); }
  public com.google.protobuf.ByteString getRequesterPlayerIdBytes() { return getPlayerIdBytes(); }

  @java.lang.Override
  public final boolean isInitialized() {
    return true;
  }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(roomId_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 1, roomId_);
    }
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(playerId_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 2, playerId_);
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
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(com.drawgame.room.grpc.generated.BeginGameRequest prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  @java.lang.Override
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessage.BuilderParent parent) {
    return new Builder(parent);
  }

  public static final class Builder extends
      com.google.protobuf.GeneratedMessage.Builder<Builder> implements
      com.drawgame.room.grpc.generated.BeginGameRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.drawgame.room.grpc.generated.Room.internal_static_room_BeginGameRequest_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.drawgame.room.grpc.generated.Room.internal_static_room_BeginGameRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.drawgame.room.grpc.generated.BeginGameRequest.class, com.drawgame.room.grpc.generated.BeginGameRequest.Builder.class);
    }

    private Builder() {}
    private Builder(com.google.protobuf.GeneratedMessage.BuilderParent parent) { super(parent); }

    @java.lang.Override
    public Builder clear() {
      super.clear();
      roomId_ = "";
      playerId_ = "";
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.drawgame.room.grpc.generated.Room.internal_static_room_BeginGameRequest_descriptor;
    }

    @java.lang.Override
    public com.drawgame.room.grpc.generated.BeginGameRequest getDefaultInstanceForType() {
      return com.drawgame.room.grpc.generated.BeginGameRequest.getDefaultInstance();
    }

    @java.lang.Override
    public com.drawgame.room.grpc.generated.BeginGameRequest build() {
      com.drawgame.room.grpc.generated.BeginGameRequest result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.drawgame.room.grpc.generated.BeginGameRequest buildPartial() {
      com.drawgame.room.grpc.generated.BeginGameRequest result = new com.drawgame.room.grpc.generated.BeginGameRequest(this);
      result.roomId_ = roomId_;
      result.playerId_ = playerId_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.drawgame.room.grpc.generated.BeginGameRequest) {
        return mergeFrom((com.drawgame.room.grpc.generated.BeginGameRequest)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.drawgame.room.grpc.generated.BeginGameRequest other) {
      if (other == com.drawgame.room.grpc.generated.BeginGameRequest.getDefaultInstance()) return this;
      if (!other.getRoomId().isEmpty()) {
        roomId_ = other.roomId_;
        onChanged();
      }
      if (!other.getPlayerId().isEmpty()) {
        playerId_ = other.playerId_;
        onChanged();
      }
      this.mergeUnknownFields(other.getUnknownFields());
      onChanged();
      return this;
    }

    @java.lang.Override
    public final boolean isInitialized() { return true; }

    @java.lang.Override
    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0: done = true; break;
            case 10: roomId_ = input.readStringRequireUtf8(); break;
            case 18: playerId_ = input.readStringRequireUtf8(); break;
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
      } else { return (java.lang.String) ref; }
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
      } else { return (java.lang.String) ref; }
    }
    public com.google.protobuf.ByteString getPlayerIdBytes() {
      java.lang.Object ref = playerId_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        playerId_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public java.lang.String getRequesterPlayerId() { return getPlayerId(); }
    public com.google.protobuf.ByteString getRequesterPlayerIdBytes() { return getPlayerIdBytes(); }
    public Builder setPlayerId(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      playerId_ = value;
      onChanged();
      return this;
    }
  }

  private static final com.drawgame.room.grpc.generated.BeginGameRequest DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.drawgame.room.grpc.generated.BeginGameRequest();
  }

  public static com.drawgame.room.grpc.generated.BeginGameRequest getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<BeginGameRequest> PARSER = new com.google.protobuf.AbstractParser<BeginGameRequest>() {
    @java.lang.Override
    public BeginGameRequest parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      Builder builder = newBuilder();
      try {
        builder.mergeFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(builder.buildPartial());
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(e).setUnfinishedMessage(builder.buildPartial());
      }
      return builder.buildPartial();
    }
  };

  public static com.google.protobuf.Parser<BeginGameRequest> parser() { return PARSER; }

  @java.lang.Override
  public com.google.protobuf.Parser<BeginGameRequest> getParserForType() { return PARSER; }

  @java.lang.Override
  public com.drawgame.room.grpc.generated.BeginGameRequest getDefaultInstanceForType() { return DEFAULT_INSTANCE; }
}
