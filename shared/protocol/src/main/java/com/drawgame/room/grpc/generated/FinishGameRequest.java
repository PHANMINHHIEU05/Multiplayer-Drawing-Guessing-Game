package com.drawgame.room.grpc.generated;

public final class FinishGameRequest extends
    com.google.protobuf.GeneratedMessage implements
    FinishGameRequestOrBuilder {
private static final long serialVersionUID = 0L;

  private FinishGameRequest(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
    super(builder);
  }
  private FinishGameRequest() {
    roomId_ = "";
  }

  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.drawgame.room.grpc.generated.Room.internal_static_room_FinishGameRequest_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.drawgame.room.grpc.generated.Room.internal_static_room_FinishGameRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.drawgame.room.grpc.generated.FinishGameRequest.class, com.drawgame.room.grpc.generated.FinishGameRequest.Builder.class);
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
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(com.drawgame.room.grpc.generated.FinishGameRequest prototype) {
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
      com.drawgame.room.grpc.generated.FinishGameRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.drawgame.room.grpc.generated.Room.internal_static_room_FinishGameRequest_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.drawgame.room.grpc.generated.Room.internal_static_room_FinishGameRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.drawgame.room.grpc.generated.FinishGameRequest.class, com.drawgame.room.grpc.generated.FinishGameRequest.Builder.class);
    }

    private Builder() {}
    private Builder(com.google.protobuf.GeneratedMessage.BuilderParent parent) { super(parent); }

    @java.lang.Override
    public Builder clear() {
      super.clear();
      roomId_ = "";
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.drawgame.room.grpc.generated.Room.internal_static_room_FinishGameRequest_descriptor;
    }

    @java.lang.Override
    public com.drawgame.room.grpc.generated.FinishGameRequest getDefaultInstanceForType() {
      return com.drawgame.room.grpc.generated.FinishGameRequest.getDefaultInstance();
    }

    @java.lang.Override
    public com.drawgame.room.grpc.generated.FinishGameRequest build() {
      com.drawgame.room.grpc.generated.FinishGameRequest result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.drawgame.room.grpc.generated.FinishGameRequest buildPartial() {
      com.drawgame.room.grpc.generated.FinishGameRequest result = new com.drawgame.room.grpc.generated.FinishGameRequest(this);
      result.roomId_ = roomId_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.drawgame.room.grpc.generated.FinishGameRequest) {
        return mergeFrom((com.drawgame.room.grpc.generated.FinishGameRequest)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.drawgame.room.grpc.generated.FinishGameRequest other) {
      if (other == com.drawgame.room.grpc.generated.FinishGameRequest.getDefaultInstance()) return this;
      if (!other.getRoomId().isEmpty()) {
        roomId_ = other.roomId_;
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
  }

  private static final com.drawgame.room.grpc.generated.FinishGameRequest DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.drawgame.room.grpc.generated.FinishGameRequest();
  }

  public static com.drawgame.room.grpc.generated.FinishGameRequest getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<FinishGameRequest> PARSER = new com.google.protobuf.AbstractParser<FinishGameRequest>() {
    @java.lang.Override
    public FinishGameRequest parsePartialFrom(
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

  public static com.google.protobuf.Parser<FinishGameRequest> parser() { return PARSER; }

  @java.lang.Override
  public com.google.protobuf.Parser<FinishGameRequest> getParserForType() { return PARSER; }

  @java.lang.Override
  public com.drawgame.room.grpc.generated.FinishGameRequest getDefaultInstanceForType() { return DEFAULT_INSTANCE; }
}
