package org.logetd.controller;

import org.logetd.domain.user.User;
import org.logetd.dto.PaymentDeclaration;
import org.logetd.dto.PaymentPageResponse;
import org.logetd.dto.PaymentResponse;
import org.logetd.service.PaymentService;
import org.logetd.shared.ApiException;
import org.logetd.shared.PaymentMethod;
import org.logetd.shared.PaymentType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentResponse> declare(
            @RequestParam Long bookingId,
            @RequestParam PaymentType paymentType,
            @RequestParam PaymentMethod paymentMethod,
            @RequestParam Long amountMillimes,
            @RequestPart("proof") MultipartFile proof,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        PaymentDeclaration request = new PaymentDeclaration(
                bookingId, paymentType, paymentMethod, amountMillimes);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.declare(currentUser(authentication), request, proof, idempotencyKey));
    }

    @PostMapping("/{id}/confirm")
    public PaymentResponse confirm(@PathVariable Long id,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   Authentication authentication) {
        return paymentService.confirm(currentUser(authentication), id, idempotencyKey);
    }

    @GetMapping("/booking/{bookingId}")
    public PaymentPageResponse byBooking(@PathVariable Long bookingId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         Authentication authentication) {
        return paymentService.findByBooking(currentUser(authentication), bookingId, page, size);
    }

    private User currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise.");
    }
}
