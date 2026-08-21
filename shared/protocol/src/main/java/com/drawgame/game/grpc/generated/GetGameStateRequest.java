package com.drawgame.game.grpc.generated;

public final class GetGameStateRequest extends
    com.google.protobuf.GeneratedMessage implements
    GetGameStateRequestOrBuilder {
private static final long serialVersionUID = 0L;

  private GetGameStateRequest(com.google.protobuf.GeneratedMessage.Builder<?> builder) { super(builder); }
  private GetGameStateRequest() {
    roomId_ = "";
    viewerPlayerId_ = "";
  }

  public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_GetGameStateRequest_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
    return com.drawgame.game.grpc.generated.Game.internal_static_game_GetGameStateRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.drawgame.game.grpc.generated.GetGameStateRequest.class, com.drawgame.game.grpc.generated.GetGameStateRequest.Builder.class);
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

  private volatile java.lang.Object viewerPlayerId_ = "";
  public java.lang.String getViewerPlayerId() {
    java.lang.Object ref = viewerPlayerId_;
    if (ref instanceof java.lang.String) return (java.lang.String) ref;
    else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      viewerPlayerId_ = s;
      return s;
    }
  }
  public com.google.protobuf.ByteString getViewerPlayerIdBytes() {
    java.lang.Object ref = viewerPlayerId_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      viewerPlayerId_ = b;
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
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(viewerPlayerId_)) {
      com.google.protobuf.GeneratedMessage.writeString(output, 2, viewerPlayerId_);
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
    if (!com.google.protobuf.GeneratedMessage.isStringEmpty(viewerPlayerId_)) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(2, viewerPlayerId_);
    }
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() { return DEFAULT_INSTANCE.toBuilder(); }
  public static Builder newBuilder(com.drawgame.game.grpc.generated.GetGameStateRequest prototype) {
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
      com.drawgame.game.grpc.generated.GetGameStateRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_GetGameStateRequest_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_GetGameStateRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.drawgame.game.grpc.generated.GetGameStateRequest.class, com.drawgame.game.grpc.generated.GetGameStateRequest.Builder.class);
    }

    private Builder() {}
    private Builder(com.google.protobuf.GeneratedMessage.BuilderParent parent) { super(parent); }

    @java.lang.Override
    public Builder clear() {
      super.clear();
      roomId_ = "";
      viewerPlayerId_ = "";
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
      return com.drawgame.game.grpc.generated.Game.internal_static_game_GetGameStateRequest_descriptor;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.GetGameStateRequest getDefaultInstanceForType() {
      return com.drawgame.game.grpc.generated.GetGameStateRequest.getDefaultInstance();
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.GetGameStateRequest build() {
      com.drawgame.game.grpc.generated.GetGameStateRequest result = buildPartial();
      if (!result.isInitialized()) throw newUninitializedMessageException(result);
      return result;
    }

    @java.lang.Override
    public com.drawgame.game.grpc.generated.GetGameStateRequest buildPartial() {
      com.drawgame.game.grpc.generated.GetGameStateRequest result = new com.drawgame.game.grpc.generated.GetGameStateRequest(this);
      result.roomId_ = roomId_;
      result.viewerPlayerId_ = viewerPlayerId_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.drawgame.game.grpc.generated.GetGameStateRequest) {
        return mergeFrom((com.drawgame.game.grpc.generated.GetGameStateRequest)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.drawgame.game.grpc.generated.GetGameStateRequest other) {
      if (other == com.drawgame.game.grpc.generated.GetGameStateRequest.getDefaultInstance()) return this;
      if (!other.getRoomId().isEmpty()) { roomId_ = other.roomId_; onChanged(); }
      if (!other.getViewerPlayerId().isEmpty()) { viewerPlayerId_ = other.viewerPlayerId_; onChanged(); }
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
            case 18: viewerPlayerId_ = input.readStringRequireUtf8(); break;
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

    private java.lang.Object viewerPlayerId_ = "";
    public java.lang.String getViewerPlayerId() {
      java.lang.Object ref = viewerPlayerId_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        viewerPlayerId_ = s;
        return s;
      } else return (java.lang.String) ref;
    }
    public com.google.protobuf.ByteString getViewerPlayerIdBytes() {
      java.lang.Object ref = viewerPlayerId_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        viewerPlayerId_ = b;
        return b;
      } else return (com.google.protobuf.ByteString) ref;
    }
    public Builder setViewerPlayerId(java.lang.String value) {
      if (value == null) throw new NullPointerException();
      viewerPlayerId_ = value;
      onChanged();
      return this;
    }
  }

  private static final com.drawgame.game.grpc.generated.GetGameStateRequest DEFAULT_INSTANCE;
  static { DEFAULT_INSTANCE = new com.drawgame.game.grpc.generated.GetGameStateRequest(); }

  public static com.drawgame.game.grpc.generated.GetGameStateRequest getDefaultInstance() { return DEFAULT_INSTANCE; }

  private static final com.google.protobuf.Parser<GetGameStateRequest> PARSER = new com.google.protobuf.AbstractParser<GetGameStateRequest>() {
    @java.lang.Override
    public GetGameStateRequest parsePartialFrom(com.google.protobuf.CodedInputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws com.google.protobuf.InvalidProtocolBufferException {
      Builder builder = newBuilder();
      try { builder.mergeFrom(input, extensionRegistry); }
      catch (com.google.protobuf.InvalidProtocolBufferException e) { throw e.setUnfinishedMessage(builder.buildPartial()); }
      catch (java.io.IOException e) { throw new com.google.protobuf.InvalidProtocolBufferException(e).setUnfinishedMessage(builder.buildPartial()); }
      return builder.buildPartial();
    }
  };

  public static com.google.protobuf.Parser<GetGameStateRequest> parser() { return PARSER; }

  @java.lang.Override
  public com.google.protobuf.Parser<GetGameStateRequest> getParserForType() { return PARSER; }

  @java.lang.Override
  public com.drawgame.game.grpc.generated.GetGameStateRequest getDefaultInstanceForType() { return DEFAULT_INSTANCE; }
}
