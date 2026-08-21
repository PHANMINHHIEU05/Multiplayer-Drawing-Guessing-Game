package com.drawgame.game.grpc.generated;

import static io.grpc.MethodDescriptor.generateFullMethodName;

@javax.annotation.Generated(
    value = "by gRPC proto compiler",
    comments = "Source: game.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class GameServiceGrpc {

  private GameServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "game.GameService";

  // Static method descriptors that insert them itself.
  private static volatile io.grpc.MethodDescriptor<com.drawgame.game.grpc.generated.StartGameRequest,
      com.drawgame.game.grpc.generated.GameStateResponse> getStartGameMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "StartGame",
      requestType = com.drawgame.game.grpc.generated.StartGameRequest.class,
      responseType = com.drawgame.game.grpc.generated.GameStateResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.drawgame.game.grpc.generated.StartGameRequest,
      com.drawgame.game.grpc.generated.GameStateResponse> getStartGameMethod() {
    io.grpc.MethodDescriptor<com.drawgame.game.grpc.generated.StartGameRequest, com.drawgame.game.grpc.generated.GameStateResponse> getStartGameMethod;
    if ((getStartGameMethod = GameServiceGrpc.getStartGameMethod) == null) {
      synchronized (GameServiceGrpc.class) {
        if ((getStartGameMethod = GameServiceGrpc.getStartGameMethod) == null) {
          GameServiceGrpc.getStartGameMethod = getStartGameMethod =
              io.grpc.MethodDescriptor.<com.drawgame.game.grpc.generated.StartGameRequest, com.drawgame.game.grpc.generated.GameStateResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StartGame"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.game.grpc.generated.StartGameRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.game.grpc.generated.GameStateResponse.getDefaultInstance()))
              .setSchemaDescriptor(new GameServiceMethodDescriptorSupplier("StartGame"))
              .build();
        }
      }
    }
    return getStartGameMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.drawgame.game.grpc.generated.GetGameStateRequest,
      com.drawgame.game.grpc.generated.GameStateResponse> getGetGameStateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetGameState",
      requestType = com.drawgame.game.grpc.generated.GetGameStateRequest.class,
      responseType = com.drawgame.game.grpc.generated.GameStateResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.drawgame.game.grpc.generated.GetGameStateRequest,
      com.drawgame.game.grpc.generated.GameStateResponse> getGetGameStateMethod() {
    io.grpc.MethodDescriptor<com.drawgame.game.grpc.generated.GetGameStateRequest, com.drawgame.game.grpc.generated.GameStateResponse> getGetGameStateMethod;
    if ((getGetGameStateMethod = GameServiceGrpc.getGetGameStateMethod) == null) {
      synchronized (GameServiceGrpc.class) {
        if ((getGetGameStateMethod = GameServiceGrpc.getGetGameStateMethod) == null) {
          GameServiceGrpc.getGetGameStateMethod = getGetGameStateMethod =
              io.grpc.MethodDescriptor.<com.drawgame.game.grpc.generated.GetGameStateRequest, com.drawgame.game.grpc.generated.GameStateResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetGameState"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.game.grpc.generated.GetGameStateRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.game.grpc.generated.GameStateResponse.getDefaultInstance()))
              .setSchemaDescriptor(new GameServiceMethodDescriptorSupplier("GetGameState"))
              .build();
        }
      }
    }
    return getGetGameStateMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.drawgame.game.grpc.generated.SubmitGuessRequest,
      com.drawgame.game.grpc.generated.GuessResponse> getSubmitGuessMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SubmitGuess",
      requestType = com.drawgame.game.grpc.generated.SubmitGuessRequest.class,
      responseType = com.drawgame.game.grpc.generated.GuessResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.drawgame.game.grpc.generated.SubmitGuessRequest,
      com.drawgame.game.grpc.generated.GuessResponse> getSubmitGuessMethod() {
    io.grpc.MethodDescriptor<com.drawgame.game.grpc.generated.SubmitGuessRequest, com.drawgame.game.grpc.generated.GuessResponse> getSubmitGuessMethod;
    if ((getSubmitGuessMethod = GameServiceGrpc.getSubmitGuessMethod) == null) {
      synchronized (GameServiceGrpc.class) {
        if ((getSubmitGuessMethod = GameServiceGrpc.getSubmitGuessMethod) == null) {
          GameServiceGrpc.getSubmitGuessMethod = getSubmitGuessMethod =
              io.grpc.MethodDescriptor.<com.drawgame.game.grpc.generated.SubmitGuessRequest, com.drawgame.game.grpc.generated.GuessResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SubmitGuess"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.game.grpc.generated.SubmitGuessRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.game.grpc.generated.GuessResponse.getDefaultInstance()))
              .setSchemaDescriptor(new GameServiceMethodDescriptorSupplier("SubmitGuess"))
              .build();
        }
      }
    }
    return getSubmitGuessMethod;
  }

  public static GameServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<GameServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<GameServiceStub>() {
        @java.lang.Override
        public GameServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new GameServiceStub(channel, callOptions);
        }
      };
    return GameServiceStub.newStub(factory, channel);
  }

  public static GameServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<GameServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<GameServiceBlockingStub>() {
        @java.lang.Override
        public GameServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new GameServiceBlockingStub(channel, callOptions);
        }
      };
    return GameServiceBlockingStub.newStub(factory, channel);
  }

  public static GameServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<GameServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<GameServiceFutureStub>() {
        @java.lang.Override
        public GameServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new GameServiceFutureStub(channel, callOptions);
        }
      };
    return GameServiceFutureStub.newStub(factory, channel);
  }

  public interface AsyncService {

    default void startGame(com.drawgame.game.grpc.generated.StartGameRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.game.grpc.generated.GameStateResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getStartGameMethod(), responseObserver);
    }

    default void getGameState(com.drawgame.game.grpc.generated.GetGameStateRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.game.grpc.generated.GameStateResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetGameStateMethod(), responseObserver);
    }

    default void submitGuess(com.drawgame.game.grpc.generated.SubmitGuessRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.game.grpc.generated.GuessResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubmitGuessMethod(), responseObserver);
    }
  }

  public static abstract class GameServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return GameServiceGrpc.bindService(this);
    }
  }

  public static final class GameServiceStub
      extends io.grpc.stub.AbstractAsyncStub<GameServiceStub> {
    private GameServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GameServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new GameServiceStub(channel, callOptions);
    }

    public void startGame(com.drawgame.game.grpc.generated.StartGameRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.game.grpc.generated.GameStateResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getStartGameMethod(), getCallOptions()), request, responseObserver);
    }

    public void getGameState(com.drawgame.game.grpc.generated.GetGameStateRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.game.grpc.generated.GameStateResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetGameStateMethod(), getCallOptions()), request, responseObserver);
    }

    public void submitGuess(com.drawgame.game.grpc.generated.SubmitGuessRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.game.grpc.generated.GuessResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSubmitGuessMethod(), getCallOptions()), request, responseObserver);
    }
  }

  public static final class GameServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<GameServiceBlockingStub> {
    private GameServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GameServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new GameServiceBlockingStub(channel, callOptions);
    }

    public com.drawgame.game.grpc.generated.GameStateResponse startGame(com.drawgame.game.grpc.generated.StartGameRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getStartGameMethod(), getCallOptions(), request);
    }

    public com.drawgame.game.grpc.generated.GameStateResponse getGameState(com.drawgame.game.grpc.generated.GetGameStateRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetGameStateMethod(), getCallOptions(), request);
    }

    public com.drawgame.game.grpc.generated.GuessResponse submitGuess(com.drawgame.game.grpc.generated.SubmitGuessRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSubmitGuessMethod(), getCallOptions(), request);
    }
  }

  public static final class GameServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<GameServiceFutureStub> {
    private GameServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GameServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new GameServiceFutureStub(channel, callOptions);
    }

    public com.google.common.util.concurrent.ListenableFuture<com.drawgame.game.grpc.generated.GameStateResponse> startGame(
        com.drawgame.game.grpc.generated.StartGameRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getStartGameMethod(), getCallOptions()), request);
    }

    public com.google.common.util.concurrent.ListenableFuture<com.drawgame.game.grpc.generated.GameStateResponse> getGameState(
        com.drawgame.game.grpc.generated.GetGameStateRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetGameStateMethod(), getCallOptions()), request);
    }

    public com.google.common.util.concurrent.ListenableFuture<com.drawgame.game.grpc.generated.GuessResponse> submitGuess(
        com.drawgame.game.grpc.generated.SubmitGuessRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSubmitGuessMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_START_GAME = 0;
  private static final int METHODID_GET_GAME_STATE = 1;
  private static final int METHODID_SUBMIT_GUESS = 2;

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
        case METHODID_START_GAME:
          serviceImpl.startGame((com.drawgame.game.grpc.generated.StartGameRequest) request,
              (io.grpc.stub.StreamObserver<com.drawgame.game.grpc.generated.GameStateResponse>) responseObserver);
          break;
        case METHODID_GET_GAME_STATE:
          serviceImpl.getGameState((com.drawgame.game.grpc.generated.GetGameStateRequest) request,
              (io.grpc.stub.StreamObserver<com.drawgame.game.grpc.generated.GameStateResponse>) responseObserver);
          break;
        case METHODID_SUBMIT_GUESS:
          serviceImpl.submitGuess((com.drawgame.game.grpc.generated.SubmitGuessRequest) request,
              (io.grpc.stub.StreamObserver<com.drawgame.game.grpc.generated.GuessResponse>) responseObserver);
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
          getStartGameMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.drawgame.game.grpc.generated.StartGameRequest,
              com.drawgame.game.grpc.generated.GameStateResponse>(
                service, METHODID_START_GAME)))
        .addMethod(
          getGetGameStateMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.drawgame.game.grpc.generated.GetGameStateRequest,
              com.drawgame.game.grpc.generated.GameStateResponse>(
                service, METHODID_GET_GAME_STATE)))
        .addMethod(
          getSubmitGuessMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.drawgame.game.grpc.generated.SubmitGuessRequest,
              com.drawgame.game.grpc.generated.GuessResponse>(
                service, METHODID_SUBMIT_GUESS)))
        .build();
  }

  private static abstract class GameServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    GameServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.drawgame.game.grpc.generated.Game.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("GameService");
    }
  }

  private static final class GameServiceFileDescriptorSupplier
      extends GameServiceBaseDescriptorSupplier {
    GameServiceFileDescriptorSupplier() {}
  }

  private static final class GameServiceMethodDescriptorSupplier
      extends GameServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    GameServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (GameServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new GameServiceFileDescriptorSupplier())
              .addMethod(getStartGameMethod())
              .addMethod(getGetGameStateMethod())
              .addMethod(getSubmitGuessMethod())
              .build();
        }
      }
    }
    return result;
  }
}
