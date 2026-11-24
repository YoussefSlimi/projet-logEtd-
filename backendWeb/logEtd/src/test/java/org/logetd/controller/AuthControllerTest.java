package org.logetd.controller;

import org.junit.jupiter.api.Test;
import org.logetd.dto.TokenResponse;
import org.logetd.dto.UserResponse;
import org.logetd.service.AuthService;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerReturns201WithoutPasswordHash() throws Exception {
        when(authService.register(any())).thenReturn(new UserResponse(
                1L, "+21612345678", "etudiant@monastir.tn", "Etudiant Test", Role.TENANT, UserStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "+21612345678",
                                  "email": "etudiant@monastir.tn",
                                  "password": "password123",
                                  "fullName": "Etudiant Test",
                                  "role": "TENANT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("etudiant@monastir.tn"))
                .andExpect(jsonPath("$.role").value("TENANT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerValidationErrorUsesStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "+21612345678", "password": "password123", "role": "TENANT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void registerAdminRoleReturns400WithBusinessCode() throws Exception {
        when(authService.register(any())).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, "REGISTER_ROLE_NOT_ALLOWED", "Rôle interdit.",
                List.of(new ApiErrorResponse.FieldIssue("role", "must be TENANT or OWNER"))));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "+21612345678",
                                  "email": "admin@monastir.tn",
                                  "password": "password123",
                                  "fullName": "Admin",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTER_ROLE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.details[0].field").value("role"));
    }

    @Test
    void loginReturnsTokens() throws Exception {
        when(authService.login(any())).thenReturn(new TokenResponse(
                "access.jwt", "refresh.jwt", "Bearer", 900L,
                new UserResponse(1L, "+21612345678", "etudiant@monastir.tn",
                        "Etudiant Test", Role.TENANT, UserStatus.ACTIVE)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier": "etudiant@monastir.tn", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh.jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void logoutReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer refresh.jwt"))
                .andExpect(status().isNoContent());
    }

    @Test
    void protectedRouteWithoutTokenReturns401WithStandardError() throws Exception {
        mockMvc.perform(get("/api/v1/listings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
