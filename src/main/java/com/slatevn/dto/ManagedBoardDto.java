package com.slatevn.dto;

import java.time.Instant;
import java.util.UUID;

public record ManagedBoardDto(
        UUID id,
        UUID workspaceId,
        String workspaceKey,
        String workspaceName,
        String name,
        UUID createdBy,
        Instant createdAt
) {
}
