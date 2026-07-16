package com.slatevn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddMembershipRequest(
        @NotNull UUID userId,
        @NotBlank String roleCode,
        String scopeType,
        UUID boardId
) {
}
