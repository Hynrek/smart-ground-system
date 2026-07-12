package ch.jp.shooting.node.hub;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

@Configuration
class HubClientConfig {

    // Spring Boot 4 / Spring 7's RestClient defaults to Jackson 3.x (tools.jackson) message conversion.
    // JsonNullableModule is Jackson 2.x only, so generated contracts models with JsonNullable<T> fields
    // (e.g. SerieSyncItem.rangeId) deserialize with a silent null instead of an empty JsonNullable unless
    // we route this client through the Jackson 2.x converter, same fix as smart-ground-hub's JacksonConfig.
    @Bean
    HubClient hubClient(@Value("${hub.base-url}") String hubBaseUrl) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JsonNullableModule());
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);

        RestClient restClient = RestClient.builder()
                .baseUrl(hubBaseUrl)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c.getClass().getSimpleName().contains("Jackson"));
                    converters.add(converter);
                })
                .build();
        return new HubClient(restClient);
    }
}
