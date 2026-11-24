package org.logetd.dto;

import org.logetd.domain.user.User;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;

public record UserResponse(Long id,
                           String phone,
                           String email,
                           String fullName,
                           Role role,
                           UserStatus status) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus()
        );
    }
}
