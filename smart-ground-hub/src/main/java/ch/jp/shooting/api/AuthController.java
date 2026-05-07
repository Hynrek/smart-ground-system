package ch.jp.shooting.api;

import ch.jp.shooting.service.JwtService;
import ch.jp.smartground.api.AuthApi;
import ch.jp.smartground.model.LoginRequest;
import ch.jp.smartground.model.LoginResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.RestController;

@RestController
@NullMarked
public class AuthController implements AuthApi {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        // Rolle aus Authentifizierung extrahieren
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5)) // ROLE_ Präfix entfernen
                .findFirst()
                .orElse(null);

        String token = jwtService.generateToken(request.getUsername(), role);
        return ResponseEntity.ok(new LoginResponse().token(token));
    }
}
