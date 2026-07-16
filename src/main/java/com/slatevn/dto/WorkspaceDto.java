package com.slatevn.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceDto(
        UUID id,
        String name,
        String key,
        UUID createdBy,
        Instant createdAt,
        Instant deletedAt
) {
}
