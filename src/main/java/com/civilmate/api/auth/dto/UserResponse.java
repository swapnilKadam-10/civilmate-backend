package com.civilmate.api.auth.dto;

import java.util.UUID;
import com.civilmate.api.auth.entity.UserAccount;

public record UserResponse(UUID id, String fullName, String email, String role) {
    public static UserResponse from(UserAccount account) {
        return new UserResponse(account.getId(), account.getFullName(), account.getEmail(), account.getRole().name());
    }
}
