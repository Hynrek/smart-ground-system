package ch.jp.shooting.node.nodechannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Node-initiierte Dauerverbindung zum Hub mit Reconnect/Backoff. Der Node wählt aus (der Hub kennt die
 * Node-IP nie); bei Abbruch verbindet der Client mit exponentiell wachsendem, gedeckeltem Backoff neu.
 * Solange verbunden, schickt er periodisch HEARTBEAT, damit der Hub seine Liveness kennt.
 */
@Component
public class NodeChannelClient {

    private static final Logger log = LoggerFactory.getLogger(NodeChannelClient.class);

    private final NodeChannelProperties props;
    private final NodeChannelCodec codec;
    private final NodeCommandHandler commandHandler;
    private final CommandDeduplicator deduplicator;

    private final StandardWebSocketClient wsClient = new StandardWebSocketClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "node-channel-client");
        t.setDaemon(true);
        return t;
    });
    private final AtomicReference<WebSocketSession> session = new AtomicReference<>();
    private final AtomicInteger attempt = new AtomicInteger(0);
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile boolean running;

    public NodeChannelClient(NodeChannelProperties props, NodeChannelCodec codec,
                             NodeCommandHandler commandHandler, CommandDeduplicator deduplicator) {
        this.props = props;
        this.codec = codec;
        this.commandHandler = commandHandler;
        this.deduplicator = deduplicator;
    }

    /** Exponentieller Backoff mit Deckel — rein, damit testbar. */
    public static long nextBackoffMs(int attempt, long initial, long max) {
        if (attempt <= 0) return initial;
        double scaled = initial * Math.pow(2, attempt);
        return (long) Math.min(scaled, max);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running = true;
        connect();
    }

    private void connect() {
        if (!running) return;
        try {
            var handler = new NodeChannelClientHandler(props, codec, commandHandler, deduplicator, this::onClosed);
            WebSocketSession s = wsClient.execute(handler, new WebSocketHttpHeaders(), URI.create(props.getHubUrl()))
                    .get(10, TimeUnit.SECONDS);
            session.set(s);
            attempt.set(0);
            startHeartbeat();
        } catch (Exception e) {
            scheduleReconnect();
        }
    }

    private void onClosed() {
        stopHeartbeat();
        session.set(null);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!running) return;
        long delay = nextBackoffMs(attempt.getAndIncrement(), props.getBackoffInitialMs(), props.getBackoffMaxMs());
        log.info("node-channel: reconnect in {} ms", delay);
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    private void startHeartbeat() {
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            WebSocketSession s = session.get();
            if (s != null && s.isOpen()) {
                try {
                    s.sendMessage(new org.springframework.web.socket.TextMessage(
                            codec.toJson(ch.jp.smartground.nodechannel.NodeChannelMessage.heartbeat(props.getNodeId()))));
                } catch (Exception e) {
                    log.debug("node-channel: Heartbeat fehlgeschlagen: {}", e.getMessage());
                }
            }
        }, props.getHeartbeatMs(), props.getHeartbeatMs(), TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> t = heartbeatTask;
        if (t != null) t.cancel(false);
    }

    public void stop() {
        running = false;
        stopHeartbeat();
        WebSocketSession s = session.getAndSet(null);
        if (s != null && s.isOpen()) {
            try { s.close(); } catch (Exception ignored) { }
        }
    }
}
