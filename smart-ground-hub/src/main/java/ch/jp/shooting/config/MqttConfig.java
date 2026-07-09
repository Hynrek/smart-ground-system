package ch.jp.shooting.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.Mqttv5PahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@NullMarked
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    static final String TOPIC_DISCOVERY  = "smartboxes/discovery";
    static final String TOPIC_STATUS     = "smartboxes/+/status";
    static final String TOPIC_CFG_ACK    = "smartboxes/+/config/ack";
    static final String TOPIC_DEVICE_EXECUTED = "smartboxes/+/device/+/executed";
    static final String TOPIC_OTA_STATUS = "smartboxes/+/ota/status";

    @Value("${mqtt.broker.url:tcp://mosquitto:1883}")
    private String brokerUrl;

    @Value("${mqtt.clientId:smartrange-backend}")
    private String clientId;

    // Dynsec login for the backend's own well-known client (username "backend" by
    // convention — see smart-ground-deploy/dynsec-init.sh). Empty defaults so the
    // app still boots against a plaintext/no-auth broker (e.g. tests) without these set.
    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    // Filesystem path to the Dev-CA's ca.crt (see smart-ground-deploy README's
    // "Cert volume/path convention"). Only needed for ssl:// broker URLs; left
    // blank for plaintext tcp:// connections (e.g. tests).
    @Value("${mqtt.tls.ca-cert-path:}")
    private String tlsCaCertPath;

    private ThreadPoolTaskExecutor inboundExecutor;
    private MessageChannel inboundChannel;

    @Bean
    public MqttConnectionOptions mqttConnectionOptions() {
        var options = new MqttConnectionOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setAutomaticReconnect(true);
        options.setCleanStart(true);

        if (!username.isBlank()) {
            options.setUserName(username);
        }
        if (!password.isBlank()) {
            options.setPassword(password.getBytes(StandardCharsets.UTF_8));
        }

        SSLSocketFactory tlsSocketFactory = buildTlsSocketFactory();
        if (tlsSocketFactory != null) {
            options.setSocketFactory(tlsSocketFactory);
        }

        return options;
    }

    /**
     * Builds a trust-only {@link SSLSocketFactory} that trusts the locally generated
     * Dev-CA (see Task A / smart-ground-deploy's mosquitto entrypoint) so Paho can
     * validate the {@code ssl://} broker's TLS certificate — the JVM's default
     * truststore has no reason to trust it. No client certificate is presented here
     * (server-cert verification only, not mTLS — the plan chose username/password
     * client auth over client certs). Returns {@code null} if no CA cert path is
     * configured, in which case the caller leaves Paho's default socket factory in
     * place (used by plaintext {@code tcp://} connections, e.g. tests).
     */
    private @Nullable SSLSocketFactory buildTlsSocketFactory() {
        if (tlsCaCertPath.isBlank()) {
            return null;
        }
        try (InputStream caInput = new FileInputStream(tlsCaCertPath)) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Certificate caCert = certificateFactory.generateCertificate(caInput);

            // Trust-only store: no password needed, holds just the one Dev-CA cert.
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, null);
            trustStore.setCertificateEntry("mqtt-dev-ca", caCert);

            TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to build MQTT TLS trust store from mqtt.tls.ca-cert-path=" + tlsCaCertPath, e);
        }
    }

    @Bean
    public Mqttv5ClientManager mqttInboundClientManager() {
        return new Mqttv5ClientManager(mqttConnectionOptions(), clientId + "-inbound");
    }

    @Bean
    public Mqttv5ClientManager mqttOutboundClientManager() {
        return new Mqttv5ClientManager(mqttConnectionOptions(), clientId + "-outbound");
    }

    @Bean
    public ThreadPoolTaskExecutor mqttInboundExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mqtt-inbound-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    public MessageChannel mqttInboundChannel(ThreadPoolTaskExecutor mqttInboundExecutor,
                                             MeterRegistry meterRegistry) {
        var channel = new ExecutorChannel(mqttInboundExecutor);
        var executor = mqttInboundExecutor.getThreadPoolExecutor();

        Gauge.builder("mqtt.inbound.queue.remaining", executor, e ->
                e.getQueue().remainingCapacity())
            .description("Remaining capacity in MQTT inbound executor queue")
            .register(meterRegistry);

        Gauge.builder("mqtt.inbound.queue.depth", executor, e ->
                e.getQueue().size())
            .description("Current depth of MQTT inbound executor queue")
            .register(meterRegistry);

        Timer.builder("mqtt.inbound.message.processing.time")
            .description("Time to process MQTT inbound messages")
            .register(meterRegistry);

        channel.addInterceptor(new ChannelInterceptor() {
            private final Timer timer = meterRegistry.timer("mqtt.inbound.message.processing.time");

            public void postSend(org.springframework.messaging.Message<?> message, boolean sent, boolean completed) {
                Long start = message.getHeaders().get("receiveTime", Long.class);
                if (start != null && start > 0) {
                    timer.record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
                }
            }
        });

        return channel;
    }

    @Bean
    public Mqttv5PahoMessageDrivenChannelAdapter mqttInboundAdapter(MessageChannel mqttInboundChannel) {
        var adapter = new Mqttv5PahoMessageDrivenChannelAdapter(
            mqttInboundClientManager(),
            TOPIC_DISCOVERY,
            TOPIC_STATUS,
            TOPIC_CFG_ACK,
            TOPIC_DEVICE_EXECUTED,
            TOPIC_OTA_STATUS
        );
        adapter.setOutputChannel(mqttInboundChannel);
        log.info("MQTT Inbound Adapter gestartet – Topics: {}, {}, {}, {}, {}",
            TOPIC_DISCOVERY, TOPIC_STATUS, TOPIC_CFG_ACK, TOPIC_DEVICE_EXECUTED, TOPIC_OTA_STATUS);
        return adapter;
    }

    @Bean
    public IntegrationFlow mqttInboundFlow(MessageChannel mqttInboundChannel,
                                           SmartBoxMqttRouter router) {
        return IntegrationFlow.from(mqttInboundChannel)
                .handle(router)
                .get();
    }

    @Bean("mqttOutboundChannel")
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public Mqttv5PahoMessageHandler mqttOutboundHandler() {
        var handler = new Mqttv5PahoMessageHandler(mqttOutboundClientManager());
        handler.setAsync(true);
        return handler;
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow() {
        return IntegrationFlow.from(mqttOutboundChannel())
                .handle(mqttOutboundHandler())
                .get();
    }
}