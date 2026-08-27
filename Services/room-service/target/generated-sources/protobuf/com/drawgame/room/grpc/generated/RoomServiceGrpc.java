package com.drawgame.room.grpc.generated;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.69.0)",
    comments = "Source: room.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class RoomServiceGrpc {

  private RoomServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "room.RoomService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.CreateRoomRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getCreateRoomMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreateRoom",
      requestType = com.drawgame.room.grpc.generated.CreateRoomRequest.class,
      responseType = com.drawgame.room.grpc.generated.RoomResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.CreateRoomRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getCreateRoomMethod() {
    io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.CreateRoomRequest, com.drawgame.room.grpc.generated.RoomResponse> getCreateRoomMethod;
    if ((getCreateRoomMethod = RoomServiceGrpc.getCreateRoomMethod) == null) {
      synchronized (RoomServiceGrpc.class) {
        if ((getCreateRoomMethod = RoomServiceGrpc.getCreateRoomMethod) == null) {
          RoomServiceGrpc.getCreateRoomMethod = getCreateRoomMethod =
              io.grpc.MethodDescriptor.<com.drawgame.room.grpc.generated.CreateRoomRequest, com.drawgame.room.grpc.generated.RoomResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateRoom"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.CreateRoomRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.RoomResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RoomServiceMethodDescriptorSupplier("CreateRoom"))
              .build();
        }
      }
    }
    return getCreateRoomMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.GetRoomRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getGetRoomMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetRoom",
      requestType = com.drawgame.room.grpc.generated.GetRoomRequest.class,
      responseType = com.drawgame.room.grpc.generated.RoomResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.GetRoomRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getGetRoomMethod() {
    io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.GetRoomRequest, com.drawgame.room.grpc.generated.RoomResponse> getGetRoomMethod;
    if ((getGetRoomMethod = RoomServiceGrpc.getGetRoomMethod) == null) {
      synchronized (RoomServiceGrpc.class) {
        if ((getGetRoomMethod = RoomServiceGrpc.getGetRoomMethod) == null) {
          RoomServiceGrpc.getGetRoomMethod = getGetRoomMethod =
              io.grpc.MethodDescriptor.<com.drawgame.room.grpc.generated.GetRoomRequest, com.drawgame.room.grpc.generated.RoomResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetRoom"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.GetRoomRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.RoomResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RoomServiceMethodDescriptorSupplier("GetRoom"))
              .build();
        }
      }
    }
    return getGetRoomMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.JoinRoomRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getJoinRoomMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "JoinRoom",
      requestType = com.drawgame.room.grpc.generated.JoinRoomRequest.class,
      responseType = com.drawgame.room.grpc.generated.RoomResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.JoinRoomRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getJoinRoomMethod() {
    io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.JoinRoomRequest, com.drawgame.room.grpc.generated.RoomResponse> getJoinRoomMethod;
    if ((getJoinRoomMethod = RoomServiceGrpc.getJoinRoomMethod) == null) {
      synchronized (RoomServiceGrpc.class) {
        if ((getJoinRoomMethod = RoomServiceGrpc.getJoinRoomMethod) == null) {
          RoomServiceGrpc.getJoinRoomMethod = getJoinRoomMethod =
              io.grpc.MethodDescriptor.<com.drawgame.room.grpc.generated.JoinRoomRequest, com.drawgame.room.grpc.generated.RoomResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "JoinRoom"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.JoinRoomRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.RoomResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RoomServiceMethodDescriptorSupplier("JoinRoom"))
              .build();
        }
      }
    }
    return getJoinRoomMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.LeaveRoomRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getLeaveRoomMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "LeaveRoom",
      requestType = com.drawgame.room.grpc.generated.LeaveRoomRequest.class,
      responseType = com.drawgame.room.grpc.generated.RoomResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.LeaveRoomRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getLeaveRoomMethod() {
    io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.LeaveRoomRequest, com.drawgame.room.grpc.generated.RoomResponse> getLeaveRoomMethod;
    if ((getLeaveRoomMethod = RoomServiceGrpc.getLeaveRoomMethod) == null) {
      synchronized (RoomServiceGrpc.class) {
        if ((getLeaveRoomMethod = RoomServiceGrpc.getLeaveRoomMethod) == null) {
          RoomServiceGrpc.getLeaveRoomMethod = getLeaveRoomMethod =
              io.grpc.MethodDescriptor.<com.drawgame.room.grpc.generated.LeaveRoomRequest, com.drawgame.room.grpc.generated.RoomResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "LeaveRoom"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.LeaveRoomRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.RoomResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RoomServiceMethodDescriptorSupplier("LeaveRoom"))
              .build();
        }
      }
    }
    return getLeaveRoomMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.BeginGameRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getBeginGameMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "BeginGame",
      requestType = com.drawgame.room.grpc.generated.BeginGameRequest.class,
      responseType = com.drawgame.room.grpc.generated.RoomResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.BeginGameRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getBeginGameMethod() {
    io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.BeginGameRequest, com.drawgame.room.grpc.generated.RoomResponse> getBeginGameMethod;
    if ((getBeginGameMethod = RoomServiceGrpc.getBeginGameMethod) == null) {
      synchronized (RoomServiceGrpc.class) {
        if ((getBeginGameMethod = RoomServiceGrpc.getBeginGameMethod) == null) {
          RoomServiceGrpc.getBeginGameMethod = getBeginGameMethod =
              io.grpc.MethodDescriptor.<com.drawgame.room.grpc.generated.BeginGameRequest, com.drawgame.room.grpc.generated.RoomResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "BeginGame"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.BeginGameRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.RoomResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RoomServiceMethodDescriptorSupplier("BeginGame"))
              .build();
        }
      }
    }
    return getBeginGameMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.FinishGameRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getFinishGameMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "FinishGame",
      requestType = com.drawgame.room.grpc.generated.FinishGameRequest.class,
      responseType = com.drawgame.room.grpc.generated.RoomResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.FinishGameRequest,
      com.drawgame.room.grpc.generated.RoomResponse> getFinishGameMethod() {
    io.grpc.MethodDescriptor<com.drawgame.room.grpc.generated.FinishGameRequest, com.drawgame.room.grpc.generated.RoomResponse> getFinishGameMethod;
    if ((getFinishGameMethod = RoomServiceGrpc.getFinishGameMethod) == null) {
      synchronized (RoomServiceGrpc.class) {
        if ((getFinishGameMethod = RoomServiceGrpc.getFinishGameMethod) == null) {
          RoomServiceGrpc.getFinishGameMethod = getFinishGameMethod =
              io.grpc.MethodDescriptor.<com.drawgame.room.grpc.generated.FinishGameRequest, com.drawgame.room.grpc.generated.RoomResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "FinishGame"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.FinishGameRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.room.grpc.generated.RoomResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RoomServiceMethodDescriptorSupplier("FinishGame"))
              .build();
        }
      }
    }
    return getFinishGameMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static RoomServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RoomServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RoomServiceStub>() {
        @java.lang.Override
        public RoomServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RoomServiceStub(channel, callOptions);
        }
      };
    return RoomServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static RoomServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RoomServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RoomServiceBlockingStub>() {
        @java.lang.Override
        public RoomServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RoomServiceBlockingStub(channel, callOptions);
        }
      };
    return RoomServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static RoomServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RoomServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RoomServiceFutureStub>() {
        @java.lang.Override
        public RoomServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RoomServiceFutureStub(channel, callOptions);
        }
      };
    return RoomServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void createRoom(com.drawgame.room.grpc.generated.CreateRoomRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreateRoomMethod(), responseObserver);
    }

    /**
     */
    default void getRoom(com.drawgame.room.grpc.generated.GetRoomRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetRoomMethod(), responseObserver);
    }

    /**
     */
    default void joinRoom(com.drawgame.room.grpc.generated.JoinRoomRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getJoinRoomMethod(), responseObserver);
    }

    /**
     */
    default void leaveRoom(com.drawgame.room.grpc.generated.LeaveRoomRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getLeaveRoomMethod(), responseObserver);
    }

    /**
     */
    default void beginGame(com.drawgame.room.grpc.generated.BeginGameRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getBeginGameMethod(), responseObserver);
    }

    /**
     */
    default void finishGame(com.drawgame.room.grpc.generated.FinishGameRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getFinishGameMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service RoomService.
   */
  public static abstract class RoomServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return RoomServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service RoomService.
   */
  public static final class RoomServiceStub
      extends io.grpc.stub.AbstractAsyncStub<RoomServiceStub> {
    private RoomServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RoomServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RoomServiceStub(channel, callOptions);
    }

    /**
     */
    public void createRoom(com.drawgame.room.grpc.generated.CreateRoomRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreateRoomMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getRoom(com.drawgame.room.grpc.generated.GetRoomRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetRoomMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void joinRoom(com.drawgame.room.grpc.generated.JoinRoomRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getJoinRoomMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void leaveRoom(com.drawgame.room.grpc.generated.LeaveRoomRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getLeaveRoomMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void beginGame(com.drawgame.room.grpc.generated.BeginGameRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getBeginGameMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void finishGame(com.drawgame.room.grpc.generated.FinishGameRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getFinishGameMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service RoomService.
   */
  public static final class RoomServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<RoomServiceBlockingStub> {
    private RoomServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RoomServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RoomServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.drawgame.room.grpc.generated.RoomResponse createRoom(com.drawgame.room.grpc.generated.CreateRoomRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateRoomMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.drawgame.room.grpc.generated.RoomResponse getRoom(com.drawgame.room.grpc.generated.GetRoomRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetRoomMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.drawgame.room.grpc.generated.RoomResponse joinRoom(com.drawgame.room.grpc.generated.JoinRoomRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getJoinRoomMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.drawgame.room.grpc.generated.RoomResponse leaveRoom(com.drawgame.room.grpc.generated.LeaveRoomRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getLeaveRoomMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.drawgame.room.grpc.generated.RoomResponse beginGame(com.drawgame.room.grpc.generated.BeginGameRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getBeginGameMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.drawgame.room.grpc.generated.RoomResponse finishGame(com.drawgame.room.grpc.generated.FinishGameRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getFinishGameMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service RoomService.
   */
  public static final class RoomServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<RoomServiceFutureStub> {
    private RoomServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RoomServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RoomServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.drawgame.room.grpc.generated.RoomResponse> createRoom(
        com.drawgame.room.grpc.generated.CreateRoomRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreateRoomMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.drawgame.room.grpc.generated.RoomResponse> getRoom(
        com.drawgame.room.grpc.generated.GetRoomRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetRoomMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.drawgame.room.grpc.generated.RoomResponse> joinRoom(
        com.drawgame.room.grpc.generated.JoinRoomRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getJoinRoomMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.drawgame.room.grpc.generated.RoomResponse> leaveRoom(
        com.drawgame.room.grpc.generated.LeaveRoomRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getLeaveRoomMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.drawgame.room.grpc.generated.RoomResponse> beginGame(
        com.drawgame.room.grpc.generated.BeginGameRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getBeginGameMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.drawgame.room.grpc.generated.RoomResponse> finishGame(
        com.drawgame.room.grpc.generated.FinishGameRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getFinishGameMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_ROOM = 0;
  private static final int METHODID_GET_ROOM = 1;
  private static final int METHODID_JOIN_ROOM = 2;
  private static final int METHODID_LEAVE_ROOM = 3;
  private static final int METHODID_BEGIN_GAME = 4;
  private static final int METHODID_FINISH_GAME = 5;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CREATE_ROOM:
          serviceImpl.createRoom((com.drawgame.room.grpc.generated.CreateRoomRequest) request,
              (io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse>) responseObserver);
          break;
        case METHODID_GET_ROOM:
          serviceImpl.getRoom((com.drawgame.room.grpc.generated.GetRoomRequest) request,
              (io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse>) responseObserver);
          break;
        case METHODID_JOIN_ROOM:
          serviceImpl.joinRoom((com.drawgame.room.grpc.generated.JoinRoomRequest) request,
              (io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse>) responseObserver);
          break;
        case METHODID_LEAVE_ROOM:
          serviceImpl.leaveRoom((com.drawgame.room.grpc.generated.LeaveRoomRequest) request,
              (io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse>) responseObserver);
          break;
        case METHODID_BEGIN_GAME:
          serviceImpl.beginGame((com.drawgame.room.grpc.generated.BeginGameRequest) request,
              (io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse>) responseObserver);
          break;
        case METHODID_FINISH_GAME:
          serviceImpl.finishGame((com.drawgame.room.grpc.generated.FinishGameRequest) request,
              (io.grpc.stub.StreamObserver<com.drawgame.room.grpc.generated.RoomResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getCreateRoomMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.drawgame.room.grpc.generated.CreateRoomRequest,
              com.drawgame.room.grpc.generated.RoomResponse>(
                service, METHODID_CREATE_ROOM)))
        .addMethod(
          getGetRoomMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.drawgame.room.grpc.generated.GetRoomRequest,
              com.drawgame.room.grpc.generated.RoomResponse>(
                service, METHODID_GET_ROOM)))
        .addMethod(
          getJoinRoomMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.drawgame.room.grpc.generated.JoinRoomRequest,
              com.drawgame.room.grpc.generated.RoomResponse>(
                service, METHODID_JOIN_ROOM)))
        .addMethod(
          getLeaveRoomMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.drawgame.room.grpc.generated.LeaveRoomRequest,
              com.drawgame.room.grpc.generated.RoomResponse>(
                service, METHODID_LEAVE_ROOM)))
        .addMethod(
          getBeginGameMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.drawgame.room.grpc.generated.BeginGameRequest,
              com.drawgame.room.grpc.generated.RoomResponse>(
                service, METHODID_BEGIN_GAME)))
        .addMethod(
          getFinishGameMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.drawgame.room.grpc.generated.FinishGameRequest,
              com.drawgame.room.grpc.generated.RoomResponse>(
                service, METHODID_FINISH_GAME)))
        .build();
  }

  private static abstract class RoomServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    RoomServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.drawgame.room.grpc.generated.Room.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("RoomService");
    }
  }

  private static final class RoomServiceFileDescriptorSupplier
      extends RoomServiceBaseDescriptorSupplier {
    RoomServiceFileDescriptorSupplier() {}
  }

  private static final class RoomServiceMethodDescriptorSupplier
      extends RoomServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    RoomServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (RoomServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new RoomServiceFileDescriptorSupplier())
              .addMethod(getCreateRoomMethod())
              .addMethod(getGetRoomMethod())
              .addMethod(getJoinRoomMethod())
              .addMethod(getLeaveRoomMethod())
              .addMethod(getBeginGameMethod())
              .addMethod(getFinishGameMethod())
              .build();
        }
      }
    }
    return result;
  }
}
