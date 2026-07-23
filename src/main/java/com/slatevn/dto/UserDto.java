package com.slatevn.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String locale,
        boolean enabled,
        List<String> systemRoles,
        List<UserMembershipDto> memberships,
        Instant createdAt,
        Instant deletedAt
) {
}
