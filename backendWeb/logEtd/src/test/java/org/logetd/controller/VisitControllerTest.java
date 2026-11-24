package org.logetd.controller;

import org.junit.jupiter.api.Test;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.VisitResponse;
import org.logetd.security.JwtService;
import org.logetd.service.VisitService;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.logetd.shared.VisitStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VisitControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private VisitService visitService;
    @MockitoBean
    private UserRepository userRepository;

    @Test
    void tenantRequestsVisitReturns201() throws Exception {
        when(visitService.request(any(), any())).thenReturn(response(VisitStatus.REQUESTED, null));

        mockMvc.perform(post("/api/v1/visits")
                        .header("Authorization", bearer(1L, Role.TENANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":10,\"scheduledAt\":\"2026-12-01T10:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.proofCode").doesNotExist());
    }

    @Test
    void ownerCannotRequestVisit() throws Exception {
        mockMvc.perform(post("/api/v1/visits")
                        .header("Authorization", bearer(2L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":10,\"scheduledAt\":\"2026-12-01T10:00:00Z\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void ownerConfirmsVisitReceivesProofCode() throws Exception {
        when(visitService.confirm(any(), eq(100L)))
                .thenReturn(response(VisitStatus.CONFIRMED, "123456"));

        mockMvc.perform(post("/api/v1/visits/100/confirm")
                        .header("Authorization", bearer(2L, Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.proofCode").value("123456"));
    }

    @Test
    void tenantCannotConfirmVisit() throws Exception {
        mockMvc.perform(post("/api/v1/visits/100/confirm")
                        .header("Authorization", bearer(1L, Role.TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void tenantProvesVisitReturns200() throws Exception {
        when(visitService.prove(any(), eq(100L), any()))
                .thenReturn(response(VisitStatus.PROVEN, null));

        mockMvc.perform(post("/api/v1/visits/100/proof")
                        .header("Authorization", bearer(1L, Role.TENANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proofCode\":\"123456\",\"latitude\":35.8,\"longitude\":10.6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROVEN"));
    }

    @Test
    void ownerCannotProveVisit() throws Exception {
        mockMvc.perform(post("/api/v1/visits/100/proof")
                        .header("Authorization", bearer(2L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proofCode\":\"123456\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private VisitResponse response(VisitStatus status, String proofCode) {
        return new VisitResponse(100L, 10L, 1L, 2L,
                Instant.now().plus(1, ChronoUnit.DAYS), status, null, proofCode);
    }

    private String bearer(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return "Bearer " + jwtService.generateAccessToken(id, role);
    }
}
