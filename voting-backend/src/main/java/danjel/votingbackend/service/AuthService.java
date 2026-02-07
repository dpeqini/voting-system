package danjel.votingbackend.service;
import danjel.votingbackend.dto.AuthRequest;
import danjel.votingbackend.dto.AuthResponse;
import danjel.votingbackend.dto.RegisterRequest;
import danjel.votingbackend.exception.AuthenticationException;
import danjel.votingbackend.exception.RegistrationException;
import danjel.votingbackend.model.Admin;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.repository.AdminRepository;
import danjel.votingbackend.repository.VoterRepository;
import danjel.votingbackend.utils.enums.UserRole;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService implements UserDetailsService {

    private final VoterRepository voterRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private static final int MAX_FAILED_ATTEMPTS = 5;

    public AuthService(VoterRepository voterRepository,
                       AdminRepository adminRepository,
                       @Lazy PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.voterRepository = voterRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Voter voter = voterRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return new User(
                voter.getEmail(),
                voter.getPasswordHash(),
                voter.isEnabled(),
                true,
                true,
                !voter.isAccountLocked(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_VOTER"))
        );
    }

    public UserDetails loadUserByUsernameAndType(String email, String userType) throws UsernameNotFoundException {
        if ("ADMIN".equals(userType)) {
            Admin admin = adminRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Admin not found with email: " + email));

            return new User(
                    admin.getEmail(),
                    admin.getPasswordHash(),
                    admin.isEnabled(),
                    true,
                    true,
                    !admin.isAccountLocked(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + admin.getRole().name()))
            );
        }

        return loadUserByUsername(email);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (voterRepository.existsByEmail(request.getEmail())) {
            throw new RegistrationException("Email already registered");
        }

        if (voterRepository.existsByNationalId(request.getNationalId())) {
            throw new RegistrationException("National ID already registered");
        }

        if (request.getDateOfBirth().isAfter(LocalDate.now().minusYears(18))) {
            throw new RegistrationException("Must be at least 18 years old to register");
        }

        Voter voter = new Voter(
                request.getNationalId(),
                request.getFirstName(),
                request.getLastName(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getDateOfBirth(),
                request.getCounty(),
                request.getMunicipality(),
                request.getAddress()
        );

        voter.setVerified(false);
        voter.setFaceVerified(false);

        voterRepository.save(voter);

        UserDetails userDetails = loadUserByUsername(voter.getEmail());
        Map<String, Object> extraClaims = createExtraClaims(voter);

        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("userType", "VOTER");

        String accessToken = jwtService.generateToken(extraClaims, userDetails);
        String refreshToken = jwtService.generateRefreshToken(refreshClaims, userDetails);

        AuthResponse response = AuthResponse.success(accessToken, refreshToken, jwtService.getExpirationTime());
        response.setVoterId(voter.getId());
        response.setEmail(voter.getEmail());
        response.setFullName(voter.getFullName());
        response.setRole(UserRole.VOTER);
        response.setVerified(voter.isVerified());
        response.setFaceVerified(voter.isFaceVerified());

        return response;
    }

    @Transactional
    public AuthResponse authenticate(AuthRequest request) {
        Voter voter = voterRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        if (voter.isAccountLocked()) {
            throw new AuthenticationException("Account is locked. Please contact support.");
        }

        if (!passwordEncoder.matches(request.getPassword(), voter.getPasswordHash())) {
            handleFailedLogin(voter);
            throw new AuthenticationException("Invalid email or password");
        }

        voterRepository.resetLoginAttempts(voter.getId());

        UserDetails userDetails = loadUserByUsername(voter.getEmail());
        Map<String, Object> extraClaims = createExtraClaims(voter);

        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("userType", "VOTER");

        String accessToken = jwtService.generateToken(extraClaims, userDetails);
        String refreshToken = jwtService.generateRefreshToken(refreshClaims, userDetails);

        AuthResponse response = AuthResponse.success(accessToken, refreshToken, jwtService.getExpirationTime());
        response.setVoterId(voter.getId());
        response.setEmail(voter.getEmail());
        response.setFullName(voter.getFullName());
        response.setRole(UserRole.VOTER);
        response.setVerified(voter.isVerified());
        response.setFaceVerified(voter.isFaceVerified());

        return response;
    }

    private void handleFailedLogin(Voter voter) {
        voterRepository.incrementFailedLoginAttempts(voter.getId());
        if (voter.getFailedLoginAttempts() + 1 >= MAX_FAILED_ATTEMPTS) {
            voterRepository.lockAccount(voter.getId());
        }
    }

    private Map<String, Object> createExtraClaims(Voter voter) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "VOTER");
        claims.put("voterId", voter.getId());
        claims.put("role", UserRole.VOTER.name());
        claims.put("county", voter.getCounty().name());
        claims.put("municipality", voter.getMunicipality().name());
        claims.put("verified", voter.isVerified());
        claims.put("faceVerified", voter.isFaceVerified());
        return claims;
    }

    public AuthResponse refreshToken(String refreshToken) {
        String email = jwtService.extractUsername(refreshToken);
        Voter voter = voterRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        UserDetails userDetails = loadUserByUsername(email);
        if (!jwtService.isTokenValid(refreshToken, userDetails)) {
            throw new AuthenticationException("Invalid refresh token");
        }

        Map<String, Object> extraClaims = createExtraClaims(voter);

        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("userType", "VOTER");

        String newAccessToken = jwtService.generateToken(extraClaims, userDetails);
        String newRefreshToken = jwtService.generateRefreshToken(refreshClaims, userDetails);

        AuthResponse response = AuthResponse.success(newAccessToken, newRefreshToken, jwtService.getExpirationTime());
        response.setVoterId(voter.getId());
        response.setEmail(voter.getEmail());
        response.setFullName(voter.getFullName());
        response.setRole(UserRole.VOTER);
        return response;
    }

    public Voter getCurrentVoter(String email) {
        return voterRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Transactional
    public void verifyVoter(String voterId) {
        Voter voter = voterRepository.findById(voterId)
                .orElseThrow(() -> new UsernameNotFoundException("Voter not found"));
        voter.setVerified(true);
        voterRepository.save(voter);
    }
}