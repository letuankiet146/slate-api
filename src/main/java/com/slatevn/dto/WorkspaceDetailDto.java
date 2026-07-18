package com.slatevn.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkspaceDetailDto(
        UUID id,
        String name,
        String key,
        UUID createdBy,
        Instant createdAt,
        List<String> permissions,
        boolean workspaceAdmin,
        String companyEmail
) {
}
