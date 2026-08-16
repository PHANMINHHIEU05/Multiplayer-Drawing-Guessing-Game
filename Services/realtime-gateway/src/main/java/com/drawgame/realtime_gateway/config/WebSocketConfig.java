
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
                        "/ws",
                        gameWebSocketHandler
                );

        SimpleUrlHandlerMapping mapping =
                new SimpleUrlHandlerMapping(
                        handlers,
                        -1
                );

        CorsConfiguration cors =
                new CorsConfiguration();

        cors.setAllowedOrigins(
                List.of(
                        "http://localhost:5173",
                        "http://localhost:8080"
                )
        );

        cors.setAllowedMethods(
                List.of("GET")
        );

        cors.setAllowedHeaders(
                List.of("*")
        );

        mapping.setCorsConfigurations(
                Map.of(
                        "/ws",
                        cors
                )
        );

        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}