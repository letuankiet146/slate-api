package com.slatevn.dto;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        UserResponse user
) {
    public static AuthResponse of(String access, String refresh, UserResponse user) {
        return new AuthResponse(access, refresh, "Bearer", user);
    }

    public record UserResponse(
            UUID id,
            String email,
            String displayName,
            String locale,
            boolean enabled,
            List<String> systemPermissions
    ) {
    }
}
