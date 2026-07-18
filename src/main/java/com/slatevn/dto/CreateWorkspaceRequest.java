package com.slatevn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWorkspaceRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(min = 2, max = 32) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String key,
        UUID adminUserId
) {
}
