package ch.jp.shooting.api;

import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.repository.auth.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    RoleRepository roleRepository;

    @InjectMocks
    RoleController roleController;

    @Test
    void listRoles_returnsAllRoles() {
        Role admin = new Role("ADMIN", "System administrator. Full system access.");
        Role shooter = new Role("SHOOTER", "User with full operational access.");
        when(roleRepository.findAll()).thenReturn(List.of(admin, shooter));

        ResponseEntity<List<RoleController.RoleResponse>> response = roleController.listRoles();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<RoleController.RoleResponse> body = response.getBody();
        assertThat(body).isNotNull().hasSize(2);
        assertThat(body.get(0).name()).isEqualTo("ADMIN");
        assertThat(body.get(0).description()).isEqualTo("System administrator. Full system access.");
        assertThat(body.get(1).name()).isEqualTo("SHOOTER");
    }

    @Test
    void listRoles_emptyRepository_returnsEmptyList() {
        when(roleRepository.findAll()).thenReturn(List.of());

        ResponseEntity<List<RoleController.RoleResponse>> response = roleController.listRoles();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isEmpty();
    }

    @Test
    void listRoles_roleWithNullDescription_mapsNullDescription() {
        Role noDesc = new Role("GUEST", null);
        when(roleRepository.findAll()).thenReturn(List.of(noDesc));

        ResponseEntity<List<RoleController.RoleResponse>> response = roleController.listRoles();

        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody().get(0).description()).isNull();
    }
}
