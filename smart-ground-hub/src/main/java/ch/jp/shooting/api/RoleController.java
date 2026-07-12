package ch.jp.shooting.api;

import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.smartground.api.RoleApi;
import ch.jp.smartground.model.RoleResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@NullMarked
public class RoleController implements RoleApi {

    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        List<RoleResponse> roles = roleRepository.findAll()
                .stream()
                .map(r -> new RoleResponse()
                        .id(r.getId())
                        .name(r.getName())
                        .description(r.getDescription()))
                .toList();
        return ResponseEntity.ok(roles);
    }
}
