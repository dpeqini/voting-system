package danjel.votingbackend.service;

import danjel.votingbackend.dto.AdminAuthResponse;
import danjel.votingbackend.dto.AdminRegisterRequest;
import danjel.votingbackend.dto.AuthRequest;
import danjel.votingbackend.exception.AuthenticationException;
import danjel.votingbackend.exception.RegistrationException;
import danjel.votingbackend.model.Admin;
import danjel.votingbackend.repository.AdminRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthService authService;

    private static final int MAX_FAILED_ATTEMPTS = 5;

    public AdminAuthService(AdminRepository adminRepository,
                            PasswordEncoder passwordEncoder,
                            JwtService jwtService,
                            AuthService authService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authService = authService;
    }

    @Transactional
    public AdminAuthResponse register(AdminRegisterRequest request) {
        if (adminRepository.existsByEmail(request.getEmail())) {
            throw new RegistrationException("Email already registered");
        }

        Admin admin = new Admin();
        admin.setEmail(request.getEmail());
        admin.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        admin.setFirstName(request.getFirstName());
        admin.setLastName(request.getLastName());
        admin.setRole(request.getRole());
        admin.setEnabled(true);

        adminRepository.save(admin);

        UserDetails userDetails = authService.loadUserByUsernameAndType(admin.getEmail(), "ADMIN");
        Map<String, Object> extraClaims = createExtraClaims(admin);

        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("userType", "ADMIN");

        String accessToken = jwtService.generateToken(extraClaims, userDetails);
        String refreshToken = jwtService.generateRefreshToken(refreshClaims, userDetails);

        AdminAuthResponse response = AdminAuthResponse.success(accessToken, refreshToken, jwtService.getExpirationTime());
        response.setAdminId(admin.getId());
        response.setEmail(admin.getEmail());
        response.setFullName(admin.getFullName());
        response.setRole(admin.getRole());

        return response;
    }

    @Transactional
    public AdminAuthResponse authenticate(AuthRequest request) {
        Admin admin = adminRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        if (admin.isAccountLocked()) {
            throw new AuthenticationException("Account is locked. Please contact support.");
        }

        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            handleFailedLogin(admin);
            throw new AuthenticationException("Invalid email or password");
        }

        adminRepository.resetLoginAttempts(admin.getId());

        UserDetails userDetails = authService.loadUserByUsernameAndType(admin.getEmail(), "ADMIN");
        Map<String, Object> extraClaims = createExtraClaims(admin);

        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("userType", "ADMIN");

        String accessToken = jwtService.generateToken(extraClaims, userDetails);
        String refreshToken = jwtService.generateRefreshToken(refreshClaims, userDetails);

        AdminAuthResponse response = AdminAuthResponse.success(accessToken, refreshToken, jwtService.getExpirationTime());
        response.setAdminId(admin.getId());
        response.setEmail(admin.getEmail());
        response.setFullName(admin.getFullName());
        response.setRole(admin.getRole());

        return response;
    }

    private void handleFailedLogin(Admin admin) {
        adminRepository.incrementFailedLoginAttempts(admin.getId());
        if (admin.getFailedLoginAttempts() + 1 >= MAX_FAILED_ATTEMPTS) {
            adminRepository.lockAccount(admin.getId());
        }
    }

    private Map<String, Object> createExtraClaims(Admin admin) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "ADMIN");
        claims.put("adminId", admin.getId());
        claims.put("role", admin.getRole().name());
        return claims;
    }

    public AdminAuthResponse refreshToken(String refreshToken) {
        String email = jwtService.extractUsername(refreshToken);
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException("Admin not found"));

        UserDetails userDetails = authService.loadUserByUsernameAndType(email, "ADMIN");
        if (!jwtService.isTokenValid(refreshToken, userDetails)) {
            throw new AuthenticationException("Invalid refresh token");
        }

        Map<String, Object> extraClaims = createExtraClaims(admin);

        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("userType", "ADMIN");

        String newAccessToken = jwtService.generateToken(extraClaims, userDetails);
        String newRefreshToken = jwtService.generateRefreshToken(refreshClaims, userDetails);

        AdminAuthResponse response = AdminAuthResponse.success(newAccessToken, newRefreshToken, jwtService.getExpirationTime());
        response.setAdminId(admin.getId());
        response.setEmail(admin.getEmail());
        response.setFullName(admin.getFullName());
        response.setRole(admin.getRole());

        return response;
    }

    public Admin getCurrentAdmin(String email) {
        return adminRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Admin not found"));
    }
}
