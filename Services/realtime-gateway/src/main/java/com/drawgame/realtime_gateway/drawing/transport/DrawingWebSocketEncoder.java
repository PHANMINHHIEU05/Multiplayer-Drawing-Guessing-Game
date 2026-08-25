package com.drawgame.realtime_gateway.drawing.transport;

import com.drawgame.realtime_gateway.drawing.protocol.BinaryDrawingEncoder;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

@Component
public class DrawingWebSocketEncoder {

    private final BinaryDrawingEncoder encoder;

    public DrawingWebSocketEncoder(BinaryDrawingEncoder encoder) {
        this.encoder = encoder;
    }

    public DrawingWebSocketEncoder() {
        this(new BinaryDrawingEncoder());
    }

    public WebSocketMessage encode(WebSocketSession session, DrawingMessage message) {
        byte[] bytes = encoder.encode(message);
        return session.binaryMessage(factory -> factory.wrap(bytes));
    }

    public byte[] encodeToBytes(DrawingMessage message) {
        return encoder.encode(message);
    }
}
