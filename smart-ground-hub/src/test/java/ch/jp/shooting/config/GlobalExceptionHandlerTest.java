package ch.jp.shooting.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = GlobalExceptionHandlerTest.TestConfig.class)
@WebAppConfiguration
class GlobalExceptionHandlerTest {

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                .httpBasic(org.springframework.security.config.Customizer.withDefaults())
                .authorizeHttpRequests(authz -> authz.anyRequest().authenticated());
            return http.build();
        }
    }

    @Autowired WebApplicationContext wac;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser
    void unmappedPath_returns404NotInternalError() throws Exception {
        mockMvc.perform(get("/api/totally-bogus-path-xyz"))
               .andExpect(status().isNotFound());
    }
}
