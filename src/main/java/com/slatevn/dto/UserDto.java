package com.slatevn.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String displayName,
        String locale,
        boolean enabled,
        List<String> systemRoles,
        Instant createdAt
) {
}
