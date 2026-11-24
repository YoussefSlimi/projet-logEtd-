package org.logetd.controller;

import jakarta.validation.Valid;
import org.logetd.domain.user.User;
import org.logetd.dto.VisitProofRequest;
import org.logetd.dto.VisitRequest;
import org.logetd.dto.VisitResponse;
import org.logetd.service.VisitService;
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
@RequestMapping("/api/v1/visits")
public class VisitController {

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @PostMapping
    public ResponseEntity<VisitResponse> request(@Valid @RequestBody VisitRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(visitService.request(currentUser(authentication), request));
    }

    @PostMapping("/{id}/confirm")
    public VisitResponse confirm(@PathVariable Long id, Authentication authentication) {
        return visitService.confirm(currentUser(authentication), id);
    }

    @PostMapping("/{id}/proof")
    public VisitResponse prove(@PathVariable Long id,
                               @Valid @RequestBody VisitProofRequest request,
                               Authentication authentication) {
        return visitService.prove(currentUser(authentication), id, request);
    }

    private User currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise.");
    }
}
