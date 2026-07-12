package ch.jp.shooting.service;

import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.auth.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Custom UserDetailsService für Spring Security
 * Lädt Benutzer aus der neuen auth-basierten User Tabelle
 * Konvertiert Rollen aus der Datenbank zu Spring Security Authorities
 */
@Service
@org.jspecify.annotations.NullMarked
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Laden eines Benutzers anhand seiner Email
     * Der Parameter 'username' akzeptiert tatsächlich die Email-Adresse
     * Dies ist die Standard Spring Security Schnittstelle
     */
    @Override
    public UserDetails loadUserByUsername(String emailOrUsername) throws UsernameNotFoundException {
        // Versuche, Benutzer anhand der Email oder Username zu finden
        // (Der Parameter wird als Email oder Username interpretiert)
        User user = userRepository.findByEmailOrUsernameWithRoles(emailOrUsername)
            .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden: " + emailOrUsername));

        // Prüfe, ob Benutzer aktiv ist
        if (user.getStatus() == User.UserStatus.INACTIVE) {
            throw new UsernameNotFoundException("Benutzerkonto ist inaktiv: " + emailOrUsername);
        }

        if (user.getStatus() == User.UserStatus.SUSPENDED) {
            throw new UsernameNotFoundException("Benutzerkonto ist gesperrt: " + emailOrUsername);
        }

        // Konvertiere Rollen zu Spring Security Authorities
        Collection<GrantedAuthority> authorities = user.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
            .collect(Collectors.toList());

        // Baue UserDetails für Spring Security
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getEmail())                          // Email als Username für Security
            .password(user.getPasswordHash())                   // Gehashtes Passwort
            .authorities(authorities)                           // Rollen aus der DB
            .accountLocked(false)
            .disabled(user.getStatus() != User.UserStatus.ACTIVE)
            .build();
    }
}
