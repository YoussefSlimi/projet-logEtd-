package org.logetd.service;

import org.logetd.domain.user.RefreshToken;
import org.logetd.domain.user.RefreshTokenRepository;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.LoginRequest;
import org.logetd.dto.RegisterRequest;
import org.logetd.dto.TokenResponse;
import org.logetd.dto.UserResponse;
import org.logetd.security.JwtService;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserResponse register(RegisterRequest request) {
        if (request.role() != Role.TENANT && request.role() != Role.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REGISTER_ROLE_NOT_ALLOWED",
                    "Seuls les rôles TENANT et OWNER sont autorisés à l'inscription.",
                    List.of(new ApiErrorResponse.FieldIssue("role", "doit être TENANT ou OWNER")));
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED",
                    "Cette adresse email est déjà utilisée.",
                    List.of(new ApiErrorResponse.FieldIssue("email", "already used")));
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_ALREADY_USED",
                    "Ce numéro de téléphone est déjà utilisé.",
                    List.of(new ApiErrorResponse.FieldIssue("phone", "already used")));
        }

        User user = new User();
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        return UserResponse.from(userRepository.save(user));
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.identifier())
                .orElseGet(() -> userRepository.findByPhone(request.identifier()).orElse(null));

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Identifiants invalides.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE",
                    "Ce compte n'est pas actif.");
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash(jwtService.hash(refreshToken));
        stored.setExpiresAt(jwtService.getRefreshTokenExpiresAt());
        refreshTokenRepository.save(stored);

        return new TokenResponse(accessToken, refreshToken, "Bearer",
                jwtService.getAccessTokenTtlSeconds(), UserResponse.from(user));
    }

    /** Révocation idempotente : un token inconnu ou déjà révoqué ne provoque pas d'erreur. */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFRESH_TOKEN_MISSING",
                    "Le refresh token est requis.",
                    List.of(new ApiErrorResponse.FieldIssue("Authorization", "Bearer <refresh token> attendu")));
        }
        refreshTokenRepository.findByTokenHash(jwtService.hash(refreshToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }
}
