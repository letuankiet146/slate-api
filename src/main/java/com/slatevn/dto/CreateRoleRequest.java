package com.slatevn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CreateRoleRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String code,
        @NotBlank String name,
        String description,
        @NotEmpty List<String> permissionCodes
) {
}
