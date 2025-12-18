package dev.backend.config;

import dev.backend.handler.WebSocketAgentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private WebSocketAgentHandler webSocketAgentHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketAgentHandler, "/ws/agent")
                .setAllowedOrigins("*")
                .addInterceptors(new HttpSessionHandshakeInterceptor());



    }
}