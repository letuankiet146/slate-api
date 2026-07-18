package com.slatevn.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProvisionTenantRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank @Size(max = 255) String displayName,
        String locale,
        @NotBlank @Size(max = 255) String workspaceName,
        @NotBlank @Size(min = 2, max = 32) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String workspaceKey,
        @Email String companyEmail
) {
}
