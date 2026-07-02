package ch.jp.shooting.service.auth;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.exception.UserNotFoundException;
import ch.jp.shooting.mapper.UserMapper;
import ch.jp.shooting.model.auth.*;
import ch.jp.shooting.repository.auth.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@org.jspecify.annotations.NullMarked
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ScopedAccessRepository scopedAccessRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       ScopedAccessRepository scopedAccessRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.scopedAccessRepository = scopedAccessRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    // 3–30 Zeichen, beginnt alphanumerisch, danach Buchstaben/Ziffern/._-
    private static final java.util.regex.Pattern USERNAME_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$");

    private void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException(
                "Invalid username: 3-30 chars, start with a letter or digit, only letters/digits/._-");
        }
    }

    // ==================== USER CRUD ====================

    /**
     * Create a new user with initial profile data
     */
    public UserDTO createUser(CreateUserRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("Email already registered");
        }

        if (request.getMitgliedsnummer() != null &&
            userRepository.findByMitgliedsnummer(request.getMitgliedsnummer()).isPresent()) {
            throw new ConflictException("Membership number already exists");
        }

        validateUsername(request.getUsername());
        if (userRepository.findByUsernameLower(User.toUsernameLower(request.getUsername())).isPresent()) {
            throw new ConflictException("Username already taken");
        }

        // Create user entity
        User user = new User(request.getEmail(), request.getVorname(), request.getNachname());
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setGeburtsdatum(request.getGeburtsdatum());
        user.setGeschlecht(request.getGeschlecht());
        user.setTelefonnummer(request.getTelefonnummer());
        user.setStrasse(request.getStrasse());
        user.setHausnummer(request.getHausnummer());
        user.setPlz(request.getPlz());
        user.setStadt(request.getStadt());
        user.setLand(request.getLand());
        user.setMitgliedsnummer(request.getMitgliedsnummer());
        user.setSprache(request.getSprache() != null ? request.getSprache() : "DE");
        user.setStatus(User.UserStatus.ACTIVE);
        user.setQrToken(UUID.randomUUID().toString()); // Neue User bekommen ihren Check-in-Token sofort

        User savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }

    /**
     * Get user by ID
     */
    public UserDTO getUserById(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        return userMapper.toDto(user);
    }

    /**
     * Get user by email
     */
    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UserNotFoundException(email));
        return userMapper.toDto(user);
    }

    // ==================== QR CHECK-IN ====================

    /** Liefert den QR-Token des Users; erzeugt ihn beim ersten Zugriff (Backfill für Alt-User). */
    public String getOrCreateQrToken(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getQrToken() == null) {
            user.setQrToken(UUID.randomUUID().toString());
            userRepository.save(user);
        }
        return Objects.requireNonNull(user.getQrToken());
    }

    /** Erzeugt einen neuen QR-Token; der alte wird damit sofort ungültig. */
    public String rotateQrToken(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        user.setQrToken(UUID.randomUUID().toString());
        userRepository.save(user);
        return Objects.requireNonNull(user.getQrToken());
    }

    /** Löst einen gescannten QR-Token zum zugehörigen User auf. */
    public UserDTO getUserByQrToken(String qrToken) {
        User user = userRepository.findByQrToken(qrToken)
            .orElseThrow(() -> new UserNotFoundException("Unbekannter QR-Token"));
        return userMapper.toDto(user);
    }

    /**
     * Update user profile (partial update)
     */
    public UserDTO updateUser(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        // Update fields if provided
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getVorname() != null) {
            user.setVorname(request.getVorname());
        }
        if (request.getNachname() != null) {
            user.setNachname(request.getNachname());
        }
        if (request.getGeburtsdatum() != null) {
            user.setGeburtsdatum(request.getGeburtsdatum());
        }
        if (request.getGeschlecht() != null) {
            user.setGeschlecht(request.getGeschlecht());
        }
        if (request.getTelefonnummer() != null) {
            user.setTelefonnummer(request.getTelefonnummer());
        }
        if (request.getTelefonBestaetigt() != null) {
            user.setTelefonBestaetigt(request.getTelefonBestaetigt());
        }
        if (request.getStrasse() != null) {
            user.setStrasse(request.getStrasse());
        }
        if (request.getHausnummer() != null) {
            user.setHausnummer(request.getHausnummer());
        }
        if (request.getPlz() != null) {
            user.setPlz(request.getPlz());
        }
        if (request.getStadt() != null) {
            user.setStadt(request.getStadt());
        }
        if (request.getLand() != null) {
            user.setLand(request.getLand());
        }
        if (request.getProfilbildUrl() != null) {
            user.setProfilbildUrl(request.getProfilbildUrl());
        }
        if (request.getBiographie() != null) {
            user.setBiographie(request.getBiographie());
        }
        if (request.getSprache() != null) {
            user.setSprache(request.getSprache());
        }
        if (request.getMitgliedsnummer() != null) {
            user.setMitgliedsnummer(request.getMitgliedsnummer());
        }
        if (request.getSchiessLizenz() != null) {
            user.setSchiessLizenz(request.getSchiessLizenz());
        }
        if (request.getSchiessLizenzVerfallsdatum() != null) {
            user.setSchiessLizenzVerfallsdatum(request.getSchiessLizenzVerfallsdatum());
        }
        if (request.getUsername() != null) {
            validateUsername(request.getUsername());
            String lower = User.toUsernameLower(request.getUsername());
            userRepository.findByUsernameLower(lower)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(o -> { throw new ConflictException("Username already taken"); });
            user.setUsername(request.getUsername());
        }
        if (request.getStatus() != null) {
            try {
                user.setStatus(User.UserStatus.valueOf(request.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + request.getStatus());
            }
        }

        User updatedUser = userRepository.save(user);
        return userMapper.toDto(updatedUser);
    }

    /**
     * Soft delete user (sets status to INACTIVE and deleted_at timestamp)
     */
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.setStatus(User.UserStatus.INACTIVE);
        user.setGeloeschtAm(java.time.Instant.now());
        userRepository.save(user);
    }

    /**
     * List all users with pagination
     */
    public Page<UserDTO> listUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        return new PageImpl<>(
            users.getContent().stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList()),
            pageable,
            users.getTotalElements()
        );
    }

    // ==================== ROLE MANAGEMENT ====================

    /**
     * Weist einem Benutzer eine Rolle zu (ohne Bereichsbeschränkung)
     */
    public UserRoleDto assignRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));

        // Prüfen ob Rolle bereits zugewiesen
        boolean alreadyAssigned = user.getUserRoles().stream()
            .anyMatch(ur -> ur.getRole().getName().equals(roleName));
        if (alreadyAssigned) {
            throw new IllegalArgumentException("User already has role: " + roleName);
        }

        user.getUserRoles().add(new ch.jp.shooting.model.auth.UserRoleEntity(user, role, null));
        userRepository.save(user);

        return new UserRoleDto(role.getId(), role.getName(), role.getDescription(), java.time.Instant.now());
    }

    /**
     * Weist einem Benutzer eine bereichsbeschränkte Rolle zu (z.B. OWNER für eine bestimmte Range)
     */
    public UserRoleDto assignScopedRole(UUID userId, String roleName, String scopeType, UUID scopeId, @Nullable java.time.Instant expiresAt) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));

        // Globale Rolle zuweisen falls noch nicht vorhanden
        boolean alreadyAssigned = user.getUserRoles().stream()
            .anyMatch(ur -> ur.getRole().getName().equals(roleName));
        if (!alreadyAssigned) {
            user.getUserRoles().add(new ch.jp.shooting.model.auth.UserRoleEntity(user, role, null));
            userRepository.save(user);
        }

        // Bereichszugriff anlegen
        ScopedAccess access = new ScopedAccess(user, role, scopeType, scopeId);
        if (expiresAt != null) {
            access.setExpiresAt(expiresAt);
        }
        scopedAccessRepository.save(access);

        UserRoleDto dto = new UserRoleDto(role.getId(), role.getName(), role.getDescription(), access.getAssignedAt());
        dto.setScopeType(scopeType);
        dto.setScopeId(scopeId);
        dto.setExpiresAt(expiresAt);
        dto.setIsExpired(false);
        return dto;
    }

    /**
     * Entzieht einem Benutzer eine globale Rolle
     */
    public void revokeRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        ch.jp.shooting.model.auth.UserRoleEntity toRemove = user.getUserRoles().stream()
            .filter(ur -> ur.getRole().getName().equals(roleName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("User does not have role: " + roleName));

        user.getUserRoles().remove(toRemove);
        userRepository.save(user);
    }

    /**
     * Revoke scoped role from a user
     */
    public void revokeScopedRole(UUID userId, String roleName, String scopeType, UUID scopeId) {
        List<ScopedAccess> accesses = scopedAccessRepository.findByUserIdAndRoleNameAndScopeId(
            userId, roleName, scopeType, scopeId);

        accesses.forEach(scopedAccessRepository::delete);
    }

    /**
     * Get all roles for a user (scoped and unscoped)
     */
    public List<UserRoleDto> getUserRoles(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        List<UserRoleDto> roles = new ArrayList<>();

        // Globale Rollen hinzufügen
        user.getUserRoles().forEach(ur -> {
            Role role = ur.getRole();
            UserRoleDto dto = new UserRoleDto(role.getId(), role.getName(), role.getDescription(), ur.getGrantedAt());
            roles.add(dto);
        });

        // Add scoped roles
        user.getScopedAccess().forEach(access -> {
            UserRoleDto dto = new UserRoleDto(
                access.getRole().getId(),
                access.getRole().getName(),
                access.getRole().getDescription(),
                access.getAssignedAt()
            );
            dto.setScopeType(access.getScopeType());
            dto.setScopeId(access.getScopeId());
            dto.setExpiresAt(access.getExpiresAt());
            dto.setIsExpired(access.isExpired());
            roles.add(dto);
        });

        return roles;
    }

    /**
     * Change user password
     */
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid current password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
