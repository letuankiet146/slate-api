package com.slatevn.dto;

import java.util.UUID;

public record MembershipDto(
        UUID id,
        UUID userId,
        String userEmail,
        String userDisplayName,
        String userAvatarUrl,
        String roleCode,
        String scopeType,
        UUID workspaceId,
        UUID boardId,
        boolean systemAdmin
) {
}
