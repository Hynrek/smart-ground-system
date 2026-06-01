package ch.jp.shooting.api;

import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.repository.auth.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RoleControllerTest.TestConfig.class)
@WebAppConfiguration
class RoleControllerTest {

    // Custom WebApplicationContext needed: @PreAuthorize on the controller
    // requires a live Spring Security proxy — MockitoExtension cannot exercise it.
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    static class TestConfig {

        @Bean
        RoleRepository roleRepository() {
            return mock(RoleRepository.class);
        }

        @Bean
        RoleController roleController(RoleRepository roleRepository) {
            return new RoleController(roleRepository);
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .httpBasic(org.springframework.security.config.Customizer.withDefaults())
                .authorizeHttpRequests(authz -> authz.anyRequest().authenticated());
            return http.build();
        }
    }

    @Autowired
    WebApplicationContext wac;

    @Autowired
    RoleRepository roleRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listRoles_returnsAllRoles() throws Exception {
        Role admin = new Role("ADMIN", "System administrator. Full system access.");
        Role shooter = new Role("SHOOTER", "User with full operational access.");
        when(roleRepository.findAll()).thenReturn(List.of(admin, shooter));

        mockMvc.perform(get("/api/roles"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2))
               .andExpect(jsonPath("$[0].name").value("ADMIN"))
               .andExpect(jsonPath("$[0].description").value("System administrator. Full system access."))
               .andExpect(jsonPath("$[1].name").value("SHOOTER"));
    }

    @Test
    void listRoles_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/roles"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SHOOTER")
    void listRoles_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/roles"))
               .andExpect(status().isForbidden());
    }
}
