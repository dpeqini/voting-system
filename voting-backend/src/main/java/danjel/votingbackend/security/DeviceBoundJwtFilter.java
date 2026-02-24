package danjel.votingbackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Device-Bound JWT Filter
 *
 * Runs AFTER JwtAuthenticationFilter (which has verified the JWT and populated SecurityContext
 * with JwtAuthenticationToken).
 *
 * Enforces:
 *  - token.deviceId must exist
 *  - request header X-Device-ID must exist
 *  - they must match (constant-time compare)
 *
 * Runs ONLY for authenticated VOTER requests (ROLE_VOTER).
 */
public class DeviceBoundJwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DeviceBoundJwtFilter.class);
    private static final String DEVICE_ID_HEADER = "X-Device-ID";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        // Apply ONLY to voters
        boolean isVoter = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_VOTER".equals(a.getAuthority()));

        if (!isVoter) {
            chain.doFilter(request, response);
            return;
        }

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            // If some other auth exists, don’t block (but log)
            log.warn("DeviceBoundJwtFilter: Expected JwtAuthenticationToken but got {} on path={}",
                    authentication.getClass().getSimpleName(), request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        String jwtDeviceId = jwtAuth.getDeviceId();
        String headerDeviceId = request.getHeader(DEVICE_ID_HEADER);

        if (jwtDeviceId == null || jwtDeviceId.isBlank()) {
            log.warn("DeviceBoundJwtFilter: token has no deviceId. path={} subject={}",
                    request.getRequestURI(), jwtAuth.getName());
            rejectRequest(response, "TOKEN_NO_DEVICE_BINDING",
                    "JWT was issued without device binding. Re-authenticate.");
            return;
        }

        if (headerDeviceId == null || headerDeviceId.isBlank()) {
            log.warn("DeviceBoundJwtFilter: missing X-Device-ID. path={} subject={}",
                    request.getRequestURI(), jwtAuth.getName());
            rejectRequest(response, "MISSING_DEVICE_ID_HEADER",
                    "X-Device-ID header is required for authenticated voter requests.");
            return;
        }

        if (!constantTimeEquals(jwtDeviceId, headerDeviceId)) {
            log.warn("DeviceBoundJwtFilter: device mismatch. path={} subject={}",
                    request.getRequestURI(), jwtAuth.getName());
            SecurityContextHolder.clearContext();
            rejectRequest(response, "DEVICE_MISMATCH",
                    "Token was not issued for this device. Re-authenticate.");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip public/auth endpoints and non-app tooling
        if (path.startsWith("/api/v1/auth/")) return true;
        if (path.startsWith("/api/v1/admin/auth/")) return true;
        if (path.startsWith("/actuator")) return true;
        if (path.startsWith("/v3/api-docs")) return true;
        if (path.startsWith("/swagger-ui")) return true;

        return false;
    }

    private void rejectRequest(HttpServletResponse response, String errorCode, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                objectMapper.writeValueAsString(Map.of(
                        "success", false,
                        "error", errorCode,
                        "message", message
                ))
        );
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}