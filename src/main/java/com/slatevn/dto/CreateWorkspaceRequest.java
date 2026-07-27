package com.slatevn.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
        @NotBlank @Size(max = 255) String name,
        String key,
        @NotBlank @Email String adminEmail,
        String adminDisplayName,
        String adminPassword
) {
}
