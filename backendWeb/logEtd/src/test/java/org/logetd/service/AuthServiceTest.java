package org.logetd.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.logetd.domain.user.RefreshToken;
import org.logetd.domain.user.RefreshTokenRepository;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.LoginRequest;
import org.logetd.dto.RegisterRequest;
import org.logetd.dto.TokenResponse;
import org.logetd.security.JwtService;
import org.logetd.shared.ApiException;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private final RegisterRequest registerRequest = new RegisterRequest(
            "+21612345678", "etudiant@monastir.tn", "password123", "Etudiant Test", Role.TENANT);

    @Test
    void registerRefusesAdminRole() {
        ApiException ex = assertThrows(ApiException.class,
                () -> authService.register(new RegisterRequest(
                        "+21612345678", "admin@monastir.tn", "password123", "Admin", Role.ADMIN)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("REGISTER_ROLE_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(userRepository);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail(registerRequest.email())).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(registerRequest));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("EMAIL_ALREADY_USED", ex.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerHashesPasswordWithBcryptCost12() {
        PasswordEncoder realEncoder = new BCryptPasswordEncoder(12);
        AuthService service = new AuthService(userRepository, refreshTokenRepository, realEncoder, jwtService);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        service.register(registerRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String hash = captor.getValue().getPasswordHash();
        assertTrue(hash.matches("^\\$2[aby]\\$12\\$.+$"), "Attendu un hash BCrypt coût 12 : " + hash);
        assertTrue(realEncoder.matches("password123", hash));
        assertEquals(UserStatus.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void loginReturnsTokensAndStoresRefreshTokenHash() {
        User user = user(7L, Role.TENANT);
        when(userRepository.findByEmail("etudiant@monastir.tn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateAccessToken(7L, Role.TENANT)).thenReturn("access.jwt");
        when(jwtService.generateRefreshToken(7L)).thenReturn("refresh.jwt");
        when(jwtService.hash("refresh.jwt")).thenReturn("emitterHash");
        when(jwtService.getRefreshTokenExpiresAt()).thenReturn(Instant.now().plusSeconds(604800));
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        TokenResponse response = authService.login(new LoginRequest("etudiant@monastir.tn", "password123"));

        assertEquals("access.jwt", response.accessToken());
        assertEquals("refresh.jwt", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresIn());
        assertEquals(7L, response.user().id());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertEquals("emitterHash", captor.getValue().getTokenHash());
        assertEquals(user, captor.getValue().getUser());
    }

    @Test
    void loginRefusesInvalidCredentials() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.findByPhone(any())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("inconnu@monastir.tn", "password123")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("INVALID_CREDENTIALS", ex.getCode());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void loginRefusesSuspendedAccount() {
        User user = user(7L, Role.TENANT);
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("etudiant@monastir.tn", "password123")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("ACCOUNT_NOT_ACTIVE", ex.getCode());
    }

    @Test
    void logoutRevokesRefreshToken() {
        RefreshToken stored = new RefreshToken();
        stored.setTokenHash("emitterHash");
        when(jwtService.hash("refresh.jwt")).thenReturn("emitterHash");
        when(refreshTokenRepository.findByTokenHash("emitterHash")).thenReturn(Optional.of(stored));

        authService.logout("refresh.jwt");

        assertNotNull(stored.getRevokedAt());
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void logoutRequiresRefreshToken() {
        ApiException ex = assertThrows(ApiException.class, () -> authService.logout(null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("REFRESH_TOKEN_MISSING", ex.getCode());
        verifyNoInteractions(refreshTokenRepository);
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail("etudiant@monastir.tn");
        user.setPhone("+21612345678");
        user.setFullName("Etudiant Test");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("$2a$12$hashDeTest");
        return user;
    }
}
