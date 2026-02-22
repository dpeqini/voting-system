package danjel.votingbackend.security;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Represents a fully validated JWT placed into the Spring SecurityContext
 * by your JwtAuthenticationFilter after verifying the token signature.
 *
 * Carries the parsed claims so controllers and interceptors can read them
 * without re-parsing the JWT token on every access.
 *
 * Usage in JwtAuthenticationFilter (after validating the token):
 *
 *   JwtAuthenticationToken auth = JwtAuthenticationToken.fromClaims(
 *       nationalId, voterId, role, county, municipality, deviceId
 *   );
 *   SecurityContextHolder.getContext().setAuthentication(auth);
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final String nationalId;   // JWT subject — the voter's ID number
    private final UUID   voterId;      // voterId claim — their database UUID
    private final String role;         // e.g. "VOTER" or "ADMIN"
    private final String county;
    private final String municipality;
    private final String deviceId;     // device ID registered during first auth

    private JwtAuthenticationToken(
            String nationalId,
            UUID voterId,
            String role,
            String county,
            String municipality,
            String deviceId,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.nationalId   = nationalId;
        this.voterId      = voterId;
        this.role         = role;
        this.county       = county;
        this.municipality = municipality;
        this.deviceId     = deviceId;
        setAuthenticated(true);  // only set true after token is verified
    }

    /**
     * Factory method — call this from your JwtAuthenticationFilter
     * after you've verified the JWT signature and parsed the claims.
     */
    public static JwtAuthenticationToken fromClaims(
            String nationalId,
            UUID   voterId,
            String role,
            String county,
            String municipality,
            String deviceId) {

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return new JwtAuthenticationToken(
                nationalId, voterId, role, county, municipality, deviceId, authorities
        );
    }

    // ── Spring Security contract ─────────────────────────────────────────────

    @Override
    public Object getCredentials() {
        return null;  // we don't hold the raw token after parsing — no need
    }

    @Override
    public Object getPrincipal() {
        return nationalId;  // the unique identifier for this voter
    }

    // ── Claim accessors ──────────────────────────────────────────────────────

    public String getNationalId()   { return nationalId;   }
    public UUID   getVoterId()      { return voterId;       }
    public String getRole()         { return role;          }
    public String getCounty()       { return county;        }
    public String getMunicipality() { return municipality;  }

    /**
     * Returns the device ID embedded in the JWT.
     * Used by DeviceSecretRegistry to look up the HMAC secret for
     * verifying X-Request-Signature on subsequent requests.
     *
     * Returns null if this JWT was issued before device-binding was added
     * (backwards-compatible — falls back to header-based device ID lookup).
     */
    public String getDeviceId() { return deviceId; }
}