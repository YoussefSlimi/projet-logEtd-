package org.logetd.controller;

import jakarta.validation.Valid;
import org.logetd.domain.user.User;
import org.logetd.dto.MessagePageResponse;
import org.logetd.dto.MessageRequest;
import org.logetd.dto.MessageResponse;
import org.logetd.service.MessagingService;
import org.logetd.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Repli REST de la messagerie : même service que le WebSocket, broadcast inclus. */
@RestController
@RequestMapping("/api/v1/messages")
public class MessagingController {

    private final MessagingService messagingService;

    public MessagingController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> send(@Valid @RequestBody MessageRequest request,
                                                Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messagingService.send(currentUser(authentication), request));
    }

    @GetMapping("/booking/{bookingId}")
    public MessagePageResponse byBooking(@PathVariable Long bookingId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         Authentication authentication) {
        return messagingService.findByBooking(currentUser(authentication), bookingId, page, size);
    }

    private User currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise.");
    }
}
