package com.slatevn.dto;

import java.util.List;
import java.util.UUID;

public record UserSessionDto(
        String primaryRole,
        String landingPath,
        boolean hasMembership,
        List<UUID> workspaceAdminWorkspaceIds,
        boolean canAccessMyTasks,
        boolean canAccessManagedBoards,
        boolean canAccessWorkspaces
) {
}
