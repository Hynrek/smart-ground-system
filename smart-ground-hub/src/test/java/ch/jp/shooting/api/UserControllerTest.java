package ch.jp.shooting.api;

import ch.jp.shooting.config.GlobalExceptionHandler;
import ch.jp.shooting.exception.UserNotFoundException;
import ch.jp.shooting.service.auth.AuthorizationService;
import ch.jp.shooting.service.auth.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserControllerTest.TestConfig.class)
@WebAppConfiguration
class UserControllerTest {

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        UserService userService() { return mock(UserService.class); }

        @Bean
        AuthorizationService authorizationService() { return mock(AuthorizationService.class); }

        @Bean
        UserController userController(UserService userService, AuthorizationService authorizationService) {
            return new UserController(userService, authorizationService);
        }

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
    @Autowired UserService userService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser
    void getUser_notFound_returns404WithProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUserById(id)).thenThrow(new UserNotFoundException(id));

        mockMvc.perform(get("/api/users/" + id))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.type").value("/errors/user-not-found"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"pw\",\"vorname\":\"Hans\",\"nachname\":\"Muster\"}"))
               .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"vorname\":\"Hans\",\"nachname\":\"Muster\"}"))
               .andExpect(status().isBadRequest());
    }

    @Test
    void getUser_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users/" + UUID.randomUUID()))
               .andExpect(status().isUnauthorized());
    }
}
