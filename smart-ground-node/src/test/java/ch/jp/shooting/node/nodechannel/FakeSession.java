package ch.jp.shooting.node.nodechannel;

import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.BlockingQueue;

/** Minimaler WebSocketSession-Stand-in für Handler-Tests: sammelt gesendete Text-Frames. */
class FakeSession implements WebSocketSession {
    private final BlockingQueue<String> sent;
    FakeSession(BlockingQueue<String> sent) { this.sent = sent; }

    @Override public void sendMessage(WebSocketMessage<?> message) {
        sent.add(((TextMessage) message).getPayload());
    }
    @Override public boolean isOpen() { return true; }
    @Override public String getId() { return "fake"; }
    // --- unused WebSocketSession members ---
    @Override public URI getUri() { return URI.create("ws://fake/node-channel"); }
    @Override public HttpHeaders getHandshakeHeaders() { return new HttpHeaders(); }
    @Override public Map<String, Object> getAttributes() { return new HashMap<>(); }
    @Override public Principal getPrincipal() { return null; }
    @Override public InetSocketAddress getLocalAddress() { return null; }
    @Override public InetSocketAddress getRemoteAddress() { return null; }
    @Override public String getAcceptedProtocol() { return null; }
    @Override public void setTextMessageSizeLimit(int messageSizeLimit) { }
    @Override public int getTextMessageSizeLimit() { return 0; }
    @Override public void setBinaryMessageSizeLimit(int messageSizeLimit) { }
    @Override public int getBinaryMessageSizeLimit() { return 0; }
    @Override public List<WebSocketExtension> getExtensions() { return List.of(); }
    @Override public void close() { }
    @Override public void close(CloseStatus status) { }
}
