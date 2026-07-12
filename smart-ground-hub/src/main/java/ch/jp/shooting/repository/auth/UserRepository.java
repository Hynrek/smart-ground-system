package ch.jp.shooting.repository.auth;

import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.model.auth.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role r LEFT JOIN FETCH r.permissions WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role r "
         + "LEFT JOIN FETCH r.permissions "
         + "WHERE LOWER(u.email) = LOWER(:login) OR u.usernameLower = LOWER(:login)")
    Optional<User> findByEmailOrUsernameWithRoles(@Param("login") String login);

    Optional<User> findByUsernameLower(String usernameLower);

    Optional<User> findByMitgliedsnummer(String mitgliedsnummer);

    Optional<User> findByQrToken(String qrToken);

    @Query("SELECT ur.role FROM UserRoleEntity ur WHERE ur.user.id = ?1")
    Set<Role> findRolesByUserId(UUID userId);
}
