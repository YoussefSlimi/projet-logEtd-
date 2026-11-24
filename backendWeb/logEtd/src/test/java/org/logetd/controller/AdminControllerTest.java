package org.logetd.controller;

import org.junit.jupiter.api.Test;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.AdminDecisionRequest;
import org.logetd.dto.AdminListingResponse;
import org.logetd.dto.AdminQueueResponse;
import org.logetd.dto.AuditLogPageResponse;
import org.logetd.security.JwtService;
import org.logetd.service.AdminService;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AdminService adminService;
    @MockitoBean
    private UserRepository userRepository;

    @Test
    void tenantCannotAccessQueue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/queue")
                        .header("Authorization", bearer(1L, Role.TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void unauthenticatedCannotAccessQueue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/queue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void adminGetsQueue() throws Exception {
        when(adminService.queue()).thenReturn(new AdminQueueResponse(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/admin/queue")
                        .header("Authorization", bearer(100L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingListings").isArray())
                .andExpect(jsonPath("$.openReports").isArray())
                .andExpect(jsonPath("$.latePayments").isArray());
    }

    @Test
    void adminValidatesListing() throws Exception {
        when(adminService.validate(any(), eq(5L))).thenReturn(decision());

        mockMvc.perform(post("/api/v1/admin/listings/5/validate")
                        .header("Authorization", bearer(100L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void adminRefusesListingWithReason() throws Exception {
        when(adminService.refuse(any(), eq(5L), any())).thenReturn(
                new AdminListingResponse(5L, "Studio", "Monastir", null, null, 2L, ListingStatus.REFUSED));

        mockMvc.perform(post("/api/v1/admin/listings/5/refuse")
                        .header("Authorization", bearer(100L, Role.ADMIN))
                        .contentType("application/json")
                        .content("{\"reason\":\"Photos trompeuses\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUSED"));

        ArgumentCaptor<AdminDecisionRequest> decision = ArgumentCaptor.forClass(AdminDecisionRequest.class);
        verify(adminService).refuse(any(), eq(5L), decision.capture());
        assertEquals("Photos trompeuses", decision.getValue().reason());
    }

    @Test
    void refusalWithoutReasonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/listings/5/refuse")
                        .header("Authorization", bearer(100L, Role.ADMIN))
                        .contentType("application/json")
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void adminGetsAuditPage() throws Exception {
        when(adminService.audit(0, 20)).thenReturn(new AuditLogPageResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/audit")
                        .header("Authorization", bearer(100L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private AdminListingResponse decision() {
        return new AdminListingResponse(5L, "Studio", "Monastir", null, null, 2L, ListingStatus.PUBLISHED);
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
