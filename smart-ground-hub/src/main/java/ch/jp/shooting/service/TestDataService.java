package ch.jp.shooting.service;

import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.model.auth.UserRoleEntity;
import ch.jp.shooting.repository.*;
import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.repository.auth.UserRoleRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// Admin-only Dev-Tooling zum Anlegen von Testdaten (Benutzer, Ranges, SmartBoxes).
@Service
@NullMarked
public class TestDataService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RangeRepository rangeRepository;
    private final SmartBoxRepository smartBoxRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final FirmwareConfigRepository firmwareConfigRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            RangeRepository rangeRepository,
            SmartBoxRepository smartBoxRepository,
            DeviceRepository deviceRepository,
            DeviceTypeRepository deviceTypeRepository,
            FirmwareConfigRepository firmwareConfigRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.rangeRepository = rangeRepository;
        this.smartBoxRepository = smartBoxRepository;
        this.deviceRepository = deviceRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.firmwareConfigRepository = firmwareConfigRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Legt einen Test-Benutzer an: Benutzername = Passwort = credential, E-Mail abgeleitet.
    @Transactional
    public User createTestUser(String credential) {
        String cred = credential == null ? "" : credential.trim();
        if (cred.isEmpty() || cred.contains("@")) {
            throw new IllegalArgumentException("credential must be non-blank and must not contain '@'");
        }
        String email = cred.toLowerCase() + "@test.local";
        if (userRepository.findByUsernameLower(cred.toLowerCase()).isPresent()) {
            throw new ConflictException("Username already in use: " + cred);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email already in use: " + email);
        }
        Role shooter = roleRepository.findByName("SHOOTER")
                .orElseThrow(() -> new IllegalStateException("SHOOTER role missing – seed did not run"));

        User user = new User(email, cred, "Test");
        user.setUsername(cred);
        user.setPasswordHash(passwordEncoder.encode(cred));
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailBestaetigt(true);
        user.setSprache("DE");
        User saved = userRepository.save(user);
        userRoleRepository.save(new UserRoleEntity(saved, shooter, null));
        return saved;
    }

    // Standard-Testplätze; idempotent nach Name.
    private static final List<String> TEST_RANGE_NAMES =
            List.of("Vorderlader", "Trapstand", "Rollhase", "Kippreh");

    public record SeededRange(Range range, boolean created) {}

    @Transactional
    public List<SeededRange> seedRanges() {
        List<SeededRange> result = new ArrayList<>();
        for (String name : TEST_RANGE_NAMES) {
            Range existing = rangeRepository.findByName(name).orElse(null);
            if (existing != null) {
                result.add(new SeededRange(existing, false));
            } else {
                Range range = new Range();
                range.setName(name);
                result.add(new SeededRange(rangeRepository.save(range), true));
            }
        }
        return result;
    }
}
