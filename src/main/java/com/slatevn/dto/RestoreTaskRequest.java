package com.slatevn.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RestoreTaskRequest(
        @NotNull UUID boardId
) {
}
