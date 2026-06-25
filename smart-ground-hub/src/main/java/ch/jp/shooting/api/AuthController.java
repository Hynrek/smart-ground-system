package ch.jp.shooting.api;

import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.service.JwtService;
import ch.jp.smartground.api.AuthApi;
import ch.jp.smartground.model.LoginRequest;
import ch.jp.smartground.model.LoginResponse;
import ch.jp.smartground.model.MeResponse;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@NullMarked
public class AuthController implements AuthApi {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RangeRepository rangeRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserRepository userRepository,
                          RangeRepository rangeRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.rangeRepository = rangeRepository;
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

        // Subject ist immer die kanonische E-Mail – egal ob per E-Mail oder Benutzername eingeloggt
        String token = jwtService.generateToken(authentication.getName(), role);
        return ResponseEntity.ok(new LoginResponse().token(token));
    }

    @Override
    public ResponseEntity<MeResponse> getMe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String email = auth.getName();

        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        List<String> permissions = user.getUserRoles().stream()
                .flatMap(ur -> ur.getRole().getPermissions().stream())
                .map(p -> p.getAction().toUpperCase())
                .distinct()
                .sorted()
                .toList();

        java.util.UUID assignedRangeId = rangeRepository.findByAssignedUserId(user.getId())
                .map(r -> r.getId())
                .orElse(null);

        MeResponse response = new MeResponse()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .vorname(user.getVorname())
                .nachname(user.getNachname())
                .geburtsdatum(user.getGeburtsdatum())
                .geschlecht(toGeschlechtEnum(user.getGeschlecht()))
                .telefonnummer(user.getTelefonnummer())
                .telefonBestaetigt(user.getTelefonBestaetigt())
                .strasse(user.getStrasse())
                .hausnummer(user.getHausnummer())
                .plz(user.getPlz())
                .stadt(user.getStadt())
                .land(user.getLand())
                .profilbildUrl(user.getProfilbildUrl())
                .biographie(user.getBiographie())
                .sprache(toSpracheEnum(user.getSprache()))
                .mitgliedsnummer(user.getMitgliedsnummer())
                .schiessLizenz(user.getSchiessLizenz())
                .schiessLizenzVerfallsdatum(user.getSchiessLizenzVerfallsdatum())
                .schiessLizenzGueltig(user.isSchiessLizenzGueltig())
                .status(MeResponse.StatusEnum.valueOf(user.getStatus().name()))
                .emailBestaetigt(user.getEmailBestaetigt())
                .letzterLogin(user.getLetzterLogin() != null ? user.getLetzterLogin().atOffset(ZoneOffset.UTC) : null)
                .erstelltAm(user.getErstelltAm().atOffset(ZoneOffset.UTC))
                .permissions(permissions)
                .assignedRangeId(assignedRangeId);

        return ResponseEntity.ok(response);
    }

    private MeResponse.@Nullable GeschlechtEnum toGeschlechtEnum(@Nullable String value) {
        if (value == null) return null;
        try {
            return MeResponse.GeschlechtEnum.fromValue(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private MeResponse.@Nullable SpracheEnum toSpracheEnum(@Nullable String value) {
        if (value == null) return null;
        try {
            return MeResponse.SpracheEnum.fromValue(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
