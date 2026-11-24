package org.logetd.controller;

import jakarta.validation.Valid;
import org.logetd.domain.user.User;
import org.logetd.dto.BookingRequest;
import org.logetd.dto.BookingResponse;
import org.logetd.service.BookingService;
import org.logetd.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> request(@Valid @RequestBody BookingRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.request(currentUser(authentication), request));
    }

    @PostMapping("/{id}/accept")
    public BookingResponse accept(@PathVariable Long id, Authentication authentication) {
        return bookingService.accept(currentUser(authentication), id);
    }

    @PostMapping("/{id}/deposit-pending")
    public BookingResponse markDepositPending(@PathVariable Long id, Authentication authentication) {
        return bookingService.markDepositPending(currentUser(authentication), id);
    }

    @PostMapping("/{id}/confirm")
    public BookingResponse confirmDeposit(@PathVariable Long id, Authentication authentication) {
        return bookingService.confirmDeposit(currentUser(authentication), id);
    }

    private User currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise.");
    }
}
