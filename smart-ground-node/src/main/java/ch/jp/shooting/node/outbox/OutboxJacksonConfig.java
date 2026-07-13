package ch.jp.shooting.node.outbox;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Eigener ObjectMapper für die Outbox-Payload (TEXT-Spalte, node-intern geschrieben und
 * gelesen). Braucht dieselben Module wie HubClientConfig (JsonNullable, java.time), weil
 * die Payload-DTOs generierte contracts-Typen sind (SerieOutboxItem, PlayInstanceOutboxItem)
 * — sonst dasselbe stille NPE-Risiko, das die Sync-Fundament-CLAUDE.md für den HTTP-Client
 * dokumentiert.
 */
@Configuration
class OutboxJacksonConfig {

    @Bean("outboxObjectMapper")
    ObjectMapper outboxObjectMapper() {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JsonNullableModule());
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
