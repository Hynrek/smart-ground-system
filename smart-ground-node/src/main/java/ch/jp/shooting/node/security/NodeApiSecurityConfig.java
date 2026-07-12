package ch.jp.shooting.node.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registriert den {@link NodeApiAuthFilter} nur für /node-api/* — box-api bleibt offen. */
@Configuration
class NodeApiSecurityConfig {

    @Bean
    FilterRegistrationBean<NodeApiAuthFilter> nodeApiAuthFilter(NodeJwtVerifier verifier) {
        FilterRegistrationBean<NodeApiAuthFilter> registration = new FilterRegistrationBean<>(new NodeApiAuthFilter(verifier));
        registration.addUrlPatterns("/node-api/*");
        return registration;
    }
}
