package com.drawgame.room.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisStartupCheck implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisStartupCheck.class);

    private final RedisConnectionFactory connectionFactory;

    public RedisStartupCheck(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void run(String... args) {
        try {
            String pong = connectionFactory.getConnection().ping();
            log.info("REDIS_CONNECTION={}", pong);
        } catch (Exception e) {
            log.warn("Redis ping failed at startup: {}", e.getMessage());
        }
    }
}