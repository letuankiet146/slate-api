package com.slatevn.dto;

public record WorkspaceAdminCapabilityDto(
        boolean workspaceAdmin,
        boolean canCreateWorkspace
) {
}
