package ch.jp.shooting.config;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Konfiguration des node-channel. tokens: nodeId -> gemeinsames Geheimnis (Übergang bis #6 Service-Token). */
@ConfigurationProperties(prefix = "node-channel")
@NullMarked
public class NodeChannelProperties {

    private Map<String, String> tokens = new HashMap<>();
    private Duration heartbeatInterval = Duration.ofSeconds(10);
    private Duration staleAfter = Duration.ofSeconds(30);
    private Duration commandTimeout = Duration.ofSeconds(5);

    public Map<String, String> getTokens() { return tokens; }
    public void setTokens(Map<String, String> tokens) { this.tokens = tokens; }
    public Duration getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(Duration heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
    public Duration getStaleAfter() { return staleAfter; }
    public void setStaleAfter(Duration staleAfter) { this.staleAfter = staleAfter; }
    public Duration getCommandTimeout() { return commandTimeout; }
    public void setCommandTimeout(Duration commandTimeout) { this.commandTimeout = commandTimeout; }
}
