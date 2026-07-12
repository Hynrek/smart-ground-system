package ch.jp.shooting.config;

import ch.jp.shooting.nodechannel.NodeChannelHandler;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registriert den rohen node-channel-WebSocket unter /node-channel. Getrennt von der STOMP-Config
 * (@EnableWebSocketMessageBroker, /ws/shooting) — beide koexistieren; /ws/shooting ist client-facing,
 * /node-channel ist der Hub<->Node-Kontrollkanal. setAllowedOrigins("*") ist ok: die App-Level-Auth
 * (HELLO-Token) gatet den Kanal, nicht die Origin.
 */
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(NodeChannelProperties.class)
@NullMarked
public class NodeChannelWebSocketConfig implements WebSocketConfigurer {

    private final NodeChannelHandler handler;

    public NodeChannelWebSocketConfig(NodeChannelHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/node-channel").setAllowedOrigins("*");
    }
}
