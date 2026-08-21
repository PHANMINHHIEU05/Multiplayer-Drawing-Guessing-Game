package com.drawgame.chat.grpc.generated;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.69.0)",
    comments = "Source: chat.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ChatServiceGrpc {

  private ChatServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "chat.ChatService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.drawgame.chat.grpc.generated.SendMessageRequest,
      com.drawgame.chat.grpc.generated.ChatMessageResponse> getSendMessageMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SendMessage",
      requestType = com.drawgame.chat.grpc.generated.SendMessageRequest.class,
      responseType = com.drawgame.chat.grpc.generated.ChatMessageResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.drawgame.chat.grpc.generated.SendMessageRequest,
      com.drawgame.chat.grpc.generated.ChatMessageResponse> getSendMessageMethod() {
    io.grpc.MethodDescriptor<com.drawgame.chat.grpc.generated.SendMessageRequest, com.drawgame.chat.grpc.generated.ChatMessageResponse> getSendMessageMethod;
    if ((getSendMessageMethod = ChatServiceGrpc.getSendMessageMethod) == null) {
      synchronized (ChatServiceGrpc.class) {
        if ((getSendMessageMethod = ChatServiceGrpc.getSendMessageMethod) == null) {
          ChatServiceGrpc.getSendMessageMethod = getSendMessageMethod =
              io.grpc.MethodDescriptor.<com.drawgame.chat.grpc.generated.SendMessageRequest, com.drawgame.chat.grpc.generated.ChatMessageResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SendMessage"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.chat.grpc.generated.SendMessageRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.chat.grpc.generated.ChatMessageResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ChatServiceMethodDescriptorSupplier("SendMessage"))
              .build();
        }
      }
    }
    return getSendMessageMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.drawgame.chat.grpc.generated.GetRecentMessagesRequest,
      com.drawgame.chat.grpc.generated.GetRecentMessagesResponse> getGetRecentMessagesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetRecentMessages",
      requestType = com.drawgame.chat.grpc.generated.GetRecentMessagesRequest.class,
      responseType = com.drawgame.chat.grpc.generated.GetRecentMessagesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.drawgame.chat.grpc.generated.GetRecentMessagesRequest,
      com.drawgame.chat.grpc.generated.GetRecentMessagesResponse> getGetRecentMessagesMethod() {
    io.grpc.MethodDescriptor<com.drawgame.chat.grpc.generated.GetRecentMessagesRequest, com.drawgame.chat.grpc.generated.GetRecentMessagesResponse> getGetRecentMessagesMethod;
    if ((getGetRecentMessagesMethod = ChatServiceGrpc.getGetRecentMessagesMethod) == null) {
      synchronized (ChatServiceGrpc.class) {
        if ((getGetRecentMessagesMethod = ChatServiceGrpc.getGetRecentMessagesMethod) == null) {
          ChatServiceGrpc.getGetRecentMessagesMethod = getGetRecentMessagesMethod =
              io.grpc.MethodDescriptor.<com.drawgame.chat.grpc.generated.GetRecentMessagesRequest, com.drawgame.chat.grpc.generated.GetRecentMessagesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetRecentMessages"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.chat.grpc.generated.GetRecentMessagesRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.drawgame.chat.grpc.generated.GetRecentMessagesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ChatServiceMethodDescriptorSupplier("GetRecentMessages"))
              .build();
        }
      }
    }
    return getGetRecentMessagesMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ChatServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ChatServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ChatServiceStub>() {
        @java.lang.Override
        public ChatServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ChatServiceStub(channel, callOptions);
        }
      };
    return ChatServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ChatServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ChatServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ChatServiceBlockingStub>() {
        @java.lang.Override
        public ChatServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ChatServiceBlockingStub(channel, callOptions);
        }
      };
    return ChatServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ChatServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ChatServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ChatServiceFutureStub>() {
        @java.lang.Override
        public ChatServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ChatServiceFutureStub(channel, callOptions);
        }
      };
    return ChatServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void sendMessage(com.drawgame.chat.grpc.generated.SendMessageRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.chat.grpc.generated.ChatMessageResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSendMessageMethod(), responseObserver);
    }

    /**
     */
    default void getRecentMessages(com.drawgame.chat.grpc.generated.GetRecentMessagesRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.chat.grpc.generated.GetRecentMessagesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetRecentMessagesMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ChatService.
   */
  public static abstract class ChatServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ChatServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ChatService.
   */
  public static final class ChatServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ChatServiceStub> {
    private ChatServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ChatServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ChatServiceStub(channel, callOptions);
    }

    /**
     */
    public void sendMessage(com.drawgame.chat.grpc.generated.SendMessageRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.chat.grpc.generated.ChatMessageResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSendMessageMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getRecentMessages(com.drawgame.chat.grpc.generated.GetRecentMessagesRequest request,
        io.grpc.stub.StreamObserver<com.drawgame.chat.grpc.generated.GetRecentMessagesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetRecentMessagesMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ChatService.
   */
  public static final class ChatServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ChatServiceBlockingStub> {
    private ChatServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ChatServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ChatServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.drawgame.chat.grpc.generated.ChatMessageResponse sendMessage(com.drawgame.chat.grpc.generated.SendMessageRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSendMessageMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.drawgame.chat.grpc.generated.GetRecentMessagesResponse getRecentMessages(com.drawgame.chat.grpc.generated.GetRecentMessagesRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetRecentMessagesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ChatService.
   */
  public static final class ChatServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ChatServiceFutureStub> {
    private ChatServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ChatServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ChatServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.drawgame.chat.grpc.generated.ChatMessageResponse> sendMessage(
        com.drawgame.chat.grpc.generated.SendMessageRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSendMessageMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.drawgame.chat.grpc.generated.GetRecentMessagesResponse> getRecentMessages(
        com.drawgame.chat.grpc.generated.GetRecentMessagesRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetRecentMessagesMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SEND_MESSAGE = 0;
  private static final int METHODID_GET_RECENT_MESSAGES = 1;

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
        case METHODID_SEND_MESSAGE:
          serviceImpl.sendMessage((com.drawgame.chat.grpc.generated.SendMessageRequest) request,
              (io.grpc.stub.StreamObserver<com.drawgame.chat.grpc.generated.ChatMessageResponse>) responseObserver);
          break;
        case METHODID_GET_RECENT_MESSAGES:
          serviceImpl.getRecentMessages((com.drawgame.chat.grpc.generated.GetRecentMessagesRequest) request,
              (io.grpc.stub.StreamObserver<com.drawgame.chat.grpc.generated.GetRecentMessagesResponse>) responseObserver);
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
          getSendMessageMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.drawgame.chat.grpc.generated.SendMessageRequest,
              com.drawgame.chat.grpc.generated.ChatMessageResponse>(
                service, METHODID_SEND_MESSAGE)))
        .addMethod(
          getGetRecentMessagesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.drawgame.chat.grpc.generated.GetRecentMessagesRequest,
              com.drawgame.chat.grpc.generated.GetRecentMessagesResponse>(
                service, METHODID_GET_RECENT_MESSAGES)))
        .build();
  }

  private static abstract class ChatServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ChatServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.drawgame.chat.grpc.generated.Chat.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ChatService");
    }
  }

  private static final class ChatServiceFileDescriptorSupplier
      extends ChatServiceBaseDescriptorSupplier {
    ChatServiceFileDescriptorSupplier() {}
  }

  private static final class ChatServiceMethodDescriptorSupplier
      extends ChatServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ChatServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (ChatServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ChatServiceFileDescriptorSupplier())
              .addMethod(getSendMessageMethod())
              .addMethod(getGetRecentMessagesMethod())
              .build();
        }
      }
    }
    return result;
  }
}
