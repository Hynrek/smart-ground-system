package ch.jp.shooting.repository.auth;

import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.model.auth.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Optional<User> findByMitgliedsnummer(String mitgliedsnummer);

    @Query("SELECT u.roles FROM User u WHERE u.id = ?1")
    Set<Role> findRolesByUserId(UUID userId);
}
