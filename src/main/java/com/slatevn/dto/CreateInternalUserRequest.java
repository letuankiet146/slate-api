package com.slatevn.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateInternalUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @Size(min = 6, max = 128) String password,
        @NotBlank String roleCode,
        String scopeType,
        UUID boardId,
        List<UUID> boardIds
) {
}
