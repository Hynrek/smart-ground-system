package ch.jp.shooting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.jspecify.annotations.NullMarked;

/**
 * WebSocket-Konfiguration für STOMP über SockJS.
 * Ermöglicht Echtzeit-Updates für Session-Status, Leaderboards und Range-Queue.
 */
@Configuration
@EnableWebSocketMessageBroker
@NullMarked
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Bean
    public ThreadPoolTaskScheduler brokerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("websocket-broker-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-Memory-Broker konfigurieren mit Heartbeat
        config.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[]{25000, 25000})
            .setTaskScheduler(brokerTaskScheduler());

        // Prefix für Client-zu-Server-Nachrichten
        config.setApplicationDestinationPrefixes("/app");

        // Optional: Relay zu External MQTT Broker
        // config.enableStompBrokerRelay("/topic", "/queue")
        //     .setRelayHost("localhost")
        //     .setRelayPort(61613)
        //     .setClientLogin("guest")
        //     .setClientPasscode("guest");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket-Endpunkt mit SockJS-Fallback
        registry.addEndpoint("/ws/shooting")
            .setAllowedOrigins("*")
            .withSockJS();
    }
}
