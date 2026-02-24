package danjel.votingbackend.config;

import danjel.votingbackend.security.JwtAuthenticationToken;
import danjel.votingbackend.service.AuthService;
import danjel.votingbackend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Validates the Bearer JWT on every request and populates the SecurityContext.
 *
 * JWT subject semantics (must match what IdCardAuthService and AdminAuthService put in):
 *   Voter JWT  → subject = nationalId,  userType claim = "VOTER"
 *   Admin JWT  → subject = email,       userType claim = "ADMIN"
 *
 * AuthService.loadUserByUsernameAndType() handles both cases correctly.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService  jwtService;
    private final AuthService authService;

    public JwtAuthenticationFilter(JwtService jwtService, AuthService authService) {
        this.jwtService  = jwtService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            // For voters: jwtSubject = nationalId
            // For admins: jwtSubject = email
            final String jwtSubject = jwtService.extractUsername(jwt);

            if (jwtSubject != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                String userType = jwtService.extractUserType(jwt);
                if (userType == null) userType = "VOTER";

                UserDetails userDetails = authService.loadUserByUsernameAndType(jwtSubject, userType);

                if (jwtService.isTokenValid(jwt, userDetails)) {

                    // Extract voter token fields (safe even if null)
                    String voterIdStr = jwtService.extractVoterId(jwt);
                    String role = jwtService.extractRole(jwt);
                    String county = jwtService.extractCounty(jwt);
                    String municipality = jwtService.extractMunicipality(jwt);

                    // ✅ Extract deviceId (for binding)
                    String deviceId = jwtService.extractDeviceId(jwt);

                    UUID voterId = null;
                    try {
                        if (voterIdStr != null) voterId = UUID.fromString(voterIdStr);
                    } catch (Exception ignored) {}

                    JwtAuthenticationToken authToken = JwtAuthenticationToken.fromClaims(
                            jwtSubject,
                            voterId,
                            role,
                            county,
                            municipality,
                            deviceId
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            logger.warn("JWT validation failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Must match the permitAll() rules in SecurityConfig exactly
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/api/v1/admin/auth/login")
                || path.startsWith("/api/v1/admin/auth/refresh")
                || path.equals("/api/v1/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }
}