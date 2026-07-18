package com.slatevn.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceJoinRequestDto(
        UUID id,
        UUID userId,
        String userEmail,
        String userDisplayName,
        UUID workspaceId,
        String workspaceName,
        String companyEmail,
        String status,
        String roleCode,
        Instant createdAt
) {
}
