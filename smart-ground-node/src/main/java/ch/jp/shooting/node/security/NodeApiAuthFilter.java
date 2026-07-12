package ch.jp.shooting.node.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Schützt /node-api/* mit einem minimalen Bearer-JWT-Check (Signatur + Ablauf, {@link NodeJwtVerifier}).
 * box-api bleibt unberührt (Boxen sind anonyme Clients). Feingranulares Permission-Gate: offener Punkt.
 */
public class NodeApiAuthFilter extends OncePerRequestFilter {

    private static final String PROBLEM_JSON =
            "{\"type\":\"/errors/unauthenticated\",\"title\":\"Unauthorized\",\"status\":401}";

    private final NodeJwtVerifier verifier;

    public NodeApiAuthFilter(NodeJwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ") && verifier.isValid(auth.substring(7))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.getWriter().write(PROBLEM_JSON);
    }
}
