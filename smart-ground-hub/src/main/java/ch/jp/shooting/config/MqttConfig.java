package ch.jp.shooting.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.jspecify.annotations.NullMarked;
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

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@NullMarked
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    static final String TOPIC_DISCOVERY  = "smartboxes/discovery";
    static final String TOPIC_STATUS     = "smartboxes/+/status";
    static final String TOPIC_CFG_ACK    = "smartboxes/+/config/ack";
    static final String TOPIC_DEVICE_EXECUTED = "smartboxes/+/device/+/executed";

    @Value("${mqtt.broker.url:tcp://mosquitto:1883}")
    private String brokerUrl;

    @Value("${mqtt.clientId:smartrange-backend}")
    private String clientId;

    private ThreadPoolTaskExecutor inboundExecutor;
    private MessageChannel inboundChannel;

    @Bean
    public MqttConnectionOptions mqttConnectionOptions() {
        var options = new MqttConnectionOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setAutomaticReconnect(true);
        options.setCleanStart(true);
        return options;
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
            TOPIC_DEVICE_EXECUTED
        );
        adapter.setOutputChannel(mqttInboundChannel);
        log.info("MQTT Inbound Adapter gestartet – Topics: {}, {}, {}, {}",
            TOPIC_DISCOVERY, TOPIC_STATUS, TOPIC_CFG_ACK, TOPIC_DEVICE_EXECUTED);
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