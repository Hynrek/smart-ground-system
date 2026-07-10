package ch.jp.shooting.node.hub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class HubClientConfig {

    @Bean
    HubClient hubClient(@Value("${hub.base-url}") String hubBaseUrl) {
        return new HubClient(RestClient.builder().baseUrl(hubBaseUrl).build());
    }
}
