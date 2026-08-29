package com.drawgame.realtime_gateway;

import com.drawgame.realtime_gateway.grpc.ChatGrpcClient;
import com.drawgame.realtime_gateway.grpc.GameGrpcClient;
import com.drawgame.realtime_gateway.grpc.RoomGrpcClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

@SpringBootTest
class RealtimeGatewayApplicationTests {

	@MockBean
	private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

	@MockBean
	private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

	@MockBean
	private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

	@MockBean
	private ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer;

	@MockBean
	private com.drawgame.realtime_gateway.drawing.redis.DrawingRedisSubscriber drawingRedisSubscriber;

	@MockBean
	private RoomGrpcClient roomGrpcClient;

	@MockBean
	private GameGrpcClient gameGrpcClient;

	@MockBean
	private ChatGrpcClient chatGrpcClient;

	@Test
	void contextLoads() {
	}

}
