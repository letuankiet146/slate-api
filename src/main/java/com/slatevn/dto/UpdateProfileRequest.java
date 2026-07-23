package com.slatevn.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 255) String displayName,
        String avatarUrl
) {
}
