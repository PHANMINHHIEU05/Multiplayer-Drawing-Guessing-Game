
package com.drawgame.realtime_gateway.config;

import com.drawgame.realtime_gateway.websocket.GameWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.List;
import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketHandlerMapping(
            GameWebSocketHandler gameWebSocketHandler
    ) {

        Map<String, WebSocketHandler> handlers =
                Map.of(
                        "/ws", gameWebSocketHandler,
                        "/ws/game", gameWebSocketHandler
                );

        SimpleUrlHandlerMapping mapping =
                new SimpleUrlHandlerMapping(
                        handlers,
                        -1
                );

        CorsConfiguration cors =
                new CorsConfiguration();

        cors.addAllowedOriginPattern("*");

        cors.setAllowedMethods(
                List.of("GET", "POST", "OPTIONS")
        );

        cors.setAllowedHeaders(
                List.of("*")
        );

        cors.setAllowCredentials(true);

        mapping.setCorsConfigurations(
                Map.of(
                        "/ws", cors,
                        "/ws/game", cors
                )
        );

        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    public com.drawgame.realtime_gateway.drawing.protocol.BinaryDrawingDecoder binaryDrawingDecoder() {
        return new com.drawgame.realtime_gateway.drawing.protocol.BinaryDrawingDecoder();
    }

    @Bean
    public com.drawgame.realtime_gateway.drawing.protocol.BinaryDrawingEncoder binaryDrawingEncoder() {
        return new com.drawgame.realtime_gateway.drawing.protocol.BinaryDrawingEncoder();
    }
}