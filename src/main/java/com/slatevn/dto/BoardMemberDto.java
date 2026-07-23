package com.slatevn.dto;

import java.util.UUID;

public record BoardMemberDto(
        UUID userId,
        String email,
        String displayName,
        String avatarUrl
) {
}
