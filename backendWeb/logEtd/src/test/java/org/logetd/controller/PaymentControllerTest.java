package org.logetd.controller;

import org.junit.jupiter.api.Test;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.PaymentPageResponse;
import org.logetd.dto.PaymentResponse;
import org.logetd.security.JwtService;
import org.logetd.service.PaymentService;
import org.logetd.shared.ApiException;
import org.logetd.shared.PaymentMethod;
import org.logetd.shared.PaymentStatus;
import org.logetd.shared.PaymentType;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private UserRepository userRepository;

    @Test
    void tenantDeclaresPaymentWithProofReturns201() throws Exception {
        when(paymentService.declare(any(), any(), any(), eq("commande-1")))
                .thenReturn(response(PaymentStatus.DECLARED));

        declareRequest(1L, Role.TENANT, "commande-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DECLARED"))
                .andExpect(jsonPath("$.tenantDeclared").value(true))
                .andExpect(jsonPath("$.ownerConfirmed").value(false));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(paymentService).declare(any(), any(), any(), key.capture());
        assertEquals("commande-1", key.getValue());
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        when(paymentService.declare(any(), any(), any(), isNull()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_MISSING",
                        "L'en-tête Idempotency-Key est obligatoire."));

        declareRequest(1L, Role.TENANT, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISSING"));
    }

    @Test
    void ownerCannotDeclarePayment() throws Exception {
        declareRequest(2L, Role.OWNER, "commande-1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void twoDeclarationsWithSameKeyReachServiceWithSameKey() throws Exception {
        PaymentResponse response = response(PaymentStatus.DECLARED);
        when(paymentService.declare(any(), any(), any(), eq("commande-1"))).thenReturn(response);

        declareRequest(1L, Role.TENANT, "commande-1").andExpect(status().isCreated());
        declareRequest(1L, Role.TENANT, "commande-1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9));

        verify(paymentService, times(2)).declare(any(), any(), any(), eq("commande-1"));
    }

    @Test
    void ownerConfirmsPaymentReturns200() throws Exception {
        when(paymentService.confirm(any(), eq(9L), eq("confirmation-1")))
                .thenReturn(response(PaymentStatus.CONFIRMED));

        mockMvc.perform(post("/api/v1/payments/9/confirm")
                        .header("Authorization", bearer(2L, Role.OWNER))
                        .header("Idempotency-Key", "confirmation-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.ownerConfirmed").value(true));
    }

    @Test
    void tenantCannotConfirmPayment() throws Exception {
        mockMvc.perform(post("/api/v1/payments/9/confirm")
                        .header("Authorization", bearer(1L, Role.TENANT))
                        .header("Idempotency-Key", "confirmation-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void getPaymentsByBookingReturnsPage() throws Exception {
        when(paymentService.findByBooking(any(), eq(7L), eq(0), eq(20)))
                .thenReturn(new PaymentPageResponse(
                        List.of(response(PaymentStatus.DECLARED)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/payments/booking/7")
                        .header("Authorization", bearer(1L, Role.TENANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("DECLARED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private org.springframework.test.web.servlet.ResultActions declareRequest(
            Long userId, Role role, String idempotencyKey) throws Exception {
        var request = multipart("/api/v1/payments")
                .file(new MockMultipartFile("proof", "recu.pdf", "application/pdf", "contenu".getBytes()))
                .param("bookingId", "7")
                .param("paymentType", "DEPOSIT")
                .param("paymentMethod", "D17")
                .param("amountMillimes", "50000")
                .header("Authorization", bearer(userId, role));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private PaymentResponse response(PaymentStatus status) {
        return new PaymentResponse(9L, 7L, 1L, PaymentType.DEPOSIT, PaymentMethod.D17,
                50000L, status, true, status == PaymentStatus.CONFIRMED,
                Instant.now(), status == PaymentStatus.CONFIRMED ? Instant.now() : null, "proof.pdf");
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
