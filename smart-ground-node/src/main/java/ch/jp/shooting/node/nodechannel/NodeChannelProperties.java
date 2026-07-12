package ch.jp.shooting.node.nodechannel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** node-channel-Client-Konfiguration. hubUrl ist die ws(s)://-Adresse des Hub-Endpoints /node-channel. */
@ConfigurationProperties(prefix = "node-channel")
public class NodeChannelProperties {

    private String hubUrl = "ws://localhost:8080/node-channel";
    private String nodeId = "node-1";
    private String token = "secret";
    private long heartbeatMs = 10000;
    private long backoffInitialMs = 1000;
    private long backoffMaxMs = 30000;

    public String getHubUrl() { return hubUrl; }
    public void setHubUrl(String hubUrl) { this.hubUrl = hubUrl; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public long getHeartbeatMs() { return heartbeatMs; }
    public void setHeartbeatMs(long heartbeatMs) { this.heartbeatMs = heartbeatMs; }
    public long getBackoffInitialMs() { return backoffInitialMs; }
    public void setBackoffInitialMs(long backoffInitialMs) { this.backoffInitialMs = backoffInitialMs; }
    public long getBackoffMaxMs() { return backoffMaxMs; }
    public void setBackoffMaxMs(long backoffMaxMs) { this.backoffMaxMs = backoffMaxMs; }
}
