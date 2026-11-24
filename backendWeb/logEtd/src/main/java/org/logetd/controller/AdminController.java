package org.logetd.controller;

import jakarta.validation.Valid;
import org.logetd.domain.user.User;
import org.logetd.dto.AdminDecisionRequest;
import org.logetd.dto.AdminListingResponse;
import org.logetd.dto.AdminQueueResponse;
import org.logetd.dto.AuditLogPageResponse;
import org.logetd.service.AdminService;
import org.logetd.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/queue")
    public AdminQueueResponse queue() {
        return adminService.queue();
    }

    @PostMapping("/listings/{id}/validate")
    @ResponseStatus(HttpStatus.OK)
    public AdminListingResponse validate(@PathVariable Long id, Authentication authentication) {
        return adminService.validate(currentUser(authentication), id);
    }

    @PostMapping("/listings/{id}/refuse")
    @ResponseStatus(HttpStatus.OK)
    public AdminListingResponse refuse(@PathVariable Long id,
                                       @Valid @RequestBody AdminDecisionRequest decision,
                                       Authentication authentication) {
        return adminService.refuse(currentUser(authentication), id, decision);
    }

    @GetMapping("/audit")
    public AuditLogPageResponse audit(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return adminService.audit(page, size);
    }

    private User currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise.");
    }
}
