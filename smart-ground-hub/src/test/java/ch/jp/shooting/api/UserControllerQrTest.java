package ch.jp.shooting.api;

import ch.jp.shooting.config.GlobalExceptionHandler;
import ch.jp.shooting.dto.UserDTO;
import ch.jp.shooting.exception.UserNotFoundException;
import ch.jp.shooting.service.auth.AuthorizationService;
import ch.jp.shooting.service.auth.UserService;
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

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserControllerQrTest.TestConfig.class)
@WebAppConfiguration
class UserControllerQrTest {

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

    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
        UserDTO me = new UserDTO();
        me.setId(userId);
        when(userService.getUserByEmail("anna@test.local")).thenReturn(me);
    }

    @Test
    @WithMockUser(username = "anna@test.local")
    void getMyQrCode_returnsToken() throws Exception {
        when(userService.getOrCreateQrToken(userId)).thenReturn("tok-1");

        mockMvc.perform(get("/api/users/me/qr"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.qrToken").value("tok-1"));
    }

    @Test
    @WithMockUser(username = "anna@test.local")
    void rotateMyQrCode_returnsNewToken() throws Exception {
        when(userService.rotateQrToken(userId)).thenReturn("tok-2");

        mockMvc.perform(post("/api/users/me/qr/rotate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.qrToken").value("tok-2"));
    }

    @Test
    @WithMockUser(username = "anna@test.local")
    void resolveUserByQr_returnsDisplayInfo() throws Exception {
        UUID resolvedId = UUID.randomUUID();
        UserDTO resolved = new UserDTO();
        resolved.setId(resolvedId);
        resolved.setFullName("Beat Beispiel");
        when(userService.getUserByQrToken("tok-3")).thenReturn(resolved);

        mockMvc.perform(get("/api/users/by-qr/tok-3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(resolvedId.toString()))
            .andExpect(jsonPath("$.displayName").value("Beat Beispiel"));
    }

    @Test
    @WithMockUser(username = "anna@test.local")
    void resolveUserByQr_unknownTokenIs404() throws Exception {
        when(userService.getUserByQrToken("gone")).thenThrow(new UserNotFoundException("gone")); // any UserNotFoundException triggers 404 via GlobalExceptionHandler

        mockMvc.perform(get("/api/users/by-qr/gone"))
            .andExpect(status().isNotFound());
    }
}
