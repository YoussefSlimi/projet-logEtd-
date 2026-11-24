package org.logetd.controller;

import org.junit.jupiter.api.Test;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.BookingResponse;
import org.logetd.security.JwtService;
import org.logetd.service.BookingService;
import org.logetd.shared.ApiException;
import org.logetd.shared.BookingStatus;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BookingService bookingService;
    @MockitoBean
    private UserRepository userRepository;

    @Test
    void tenantCreatesBookingReturns201() throws Exception {
        when(bookingService.request(any(), any())).thenReturn(response(BookingStatus.REQUESTED));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(1L, Role.TENANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void ownerCannotCreateBooking() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(2L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void overlappingDatesReturn409() throws Exception {
        when(bookingService.request(any(), any())).thenThrow(new ApiException(
                HttpStatus.CONFLICT, "BOOKING_DATES_UNAVAILABLE", "Ces dates ne sont plus disponibles."));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(1L, Role.TENANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_DATES_UNAVAILABLE"));
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(1L, Role.TENANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2026-12-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void ownerAcceptsBookingReturns200() throws Exception {
        when(bookingService.accept(any(), eq(7L))).thenReturn(response(BookingStatus.ACCEPTED));

        mockMvc.perform(post("/api/v1/bookings/7/accept")
                        .header("Authorization", bearer(2L, Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void tenantCannotAcceptBooking() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/7/accept")
                        .header("Authorization", bearer(1L, Role.TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private String payload() {
        return """
                {"placeId":42,"startDate":"2026-12-01","endDate":"2026-12-10","depositMillimes":50000}
                """;
    }

    private BookingResponse response(BookingStatus status) {
        return new BookingResponse(7L, 42L, 10L, 1L, status,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10), 50000L);
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
