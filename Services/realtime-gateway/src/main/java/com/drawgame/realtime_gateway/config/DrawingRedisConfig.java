package com.drawgame.realtime_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

/**
 * Redis configuration for drawing Pub/Sub fanout.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link ReactiveStringRedisTemplate} — used by {@code DrawingRedisPublisher}</li>
 *   <li>{@link ReactiveRedisMessageListenerContainer} — used by {@code DrawingRedisSubscriber}</li>
 * </ul>
 *
 * <p>Connection is provided by {@code spring-boot-starter-data-redis} auto-configuration
 * via {@code spring.data.redis.*} properties.
 */
@Configuration
public class DrawingRedisConfig {

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory
    ) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }

    @Bean
    public ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer(
            ReactiveRedisConnectionFactory connectionFactory
    ) {
        return new ReactiveRedisMessageListenerContainer(connectionFactory);
    }
}
