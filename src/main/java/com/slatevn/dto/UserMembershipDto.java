package com.slatevn.dto;

import java.util.UUID;

public record UserMembershipDto(
        UUID id,
        UUID workspaceId,
        String workspaceName,
        String roleCode,
        String roleName
) {
}
