package ch.jp.shooting.api;

import ch.jp.shooting.repository.auth.RoleRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@NullMarked
public class RoleController {

    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        List<RoleResponse> roles = roleRepository.findAll()
                .stream()
                .map(r -> new RoleResponse(r.getId(), r.getName(), r.getDescription()))
                .toList();
        return ResponseEntity.ok(roles);
    }

    public record RoleResponse(UUID id, String name, @Nullable String description) {}
}
