package com.slatevn.dto;

import java.time.Instant;
import java.util.UUID;

public record BoardDto(
        UUID id,
        UUID workspaceId,
        String name,
        UUID createdBy,
        Instant createdAt,
        Instant deletedAt
) {
}
