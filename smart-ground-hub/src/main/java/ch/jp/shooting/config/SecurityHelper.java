package ch.jp.shooting.config;

import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.auth.UserRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@NullMarked
public class SecurityHelper {

    private final UserRepository userRepository;

    public SecurityHelper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Gibt den aktuell authentifizierten User zurück (wirft 401 wenn nicht angemeldet). */
    public User currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String email = auth.getName(); // JWT sub = E-Mail
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    /** Prüft ob der aktuelle Nutzer ADMIN oder GROUND_OWNER ist. */
    public boolean isAdminOrOwner() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_GROUND_OWNER"));
    }
}
