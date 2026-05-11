package ch.jp.shooting.service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Service
@NullMarked
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.expirationMs = expirationMs;
    }

    /**
     * Generates JWT token with email (stored as subject)
     * @param email User's email address
     * @return JWT token
     */
    public String generateToken(String email) {
        return generateToken(email, null);
    }

    /**
     * Generates JWT token with email and optional role
     * @param email User's email address (stored as token subject)
     * @param role Optional role name
     * @return JWT token
     */
    public String generateToken(String email, String role) {
        var builder = Jwts.builder()
                .subject(email)  // Email is stored as the subject
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs));

        if (role != null) {
            builder.claim("role", role);
        }

        return builder.signWith(key).compact();
    }

    /**
     * Extracts email from JWT token
     * @param token JWT token
     * @return Email address stored as token subject
     */
    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();  // Subject contains the email
    }

    public String extractRole(String token) {
        var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Object role = claims.get("role");
        return role != null ? role.toString() : null;
    }

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
