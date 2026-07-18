package com.slatevn.dto;

public record ProvisionTenantResponse(
        UserDto user,
        WorkspaceDto workspace
) {
}
