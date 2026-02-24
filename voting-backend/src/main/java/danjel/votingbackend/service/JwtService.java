package danjel.votingbackend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return generateRefreshToken(new HashMap<>(), userDetails);
    }

    public String generateRefreshToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, refreshExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expirationMs) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username != null
                && username.equals(userDetails.getUsername())
                && !isTokenExpired(token);
    }

    public long getExpirationTime() {
        return jwtExpiration;
    }

    // ── Existing helpers ─────────────────────────────────────────────

    public String extractUserType(String token) {
        Object val = extractClaim(token, c -> c.get("userType"));
        return val == null ? null : String.valueOf(val);
    }

    public String extractVoterId(String token) {
        Object val = extractClaim(token, c -> c.get("voterId"));
        return val == null ? null : String.valueOf(val);
    }

    public String extractRole(String token) {
        Object val = extractClaim(token, c -> c.get("role"));
        return val == null ? null : String.valueOf(val);
    }

    public String extractCounty(String token) {
        Object val = extractClaim(token, c -> c.get("county"));
        return val == null ? null : String.valueOf(val);
    }

    public String extractMunicipality(String token) {
        Object val = extractClaim(token, c -> c.get("municipality"));
        return val == null ? null : String.valueOf(val);
    }

    // ✅ NEW: deviceId claim extractor
    public String extractDeviceId(String token) {
        Object val = extractClaim(token, c -> c.get("deviceId"));
        return val == null ? null : String.valueOf(val);
    }

    // ── Internals ────────────────────────────────────────────────────

    private boolean isTokenExpired(String token) {
        Date exp = extractClaim(token, Claims::getExpiration);
        return exp.before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] bytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(bytes);
    }
}