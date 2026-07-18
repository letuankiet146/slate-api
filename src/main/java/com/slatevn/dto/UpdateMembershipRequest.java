package com.slatevn.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UpdateMembershipRequest(
        @NotBlank String roleCode,
        String scopeType,
        UUID boardId
) {
}
