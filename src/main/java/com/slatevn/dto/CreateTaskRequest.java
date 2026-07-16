package com.slatevn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank @Size(max = 512) String title,
        String description,
        UUID columnId,
        UUID assigneeId,
        @NotNull UUID templateId,
        Map<UUID, String> fieldValues
) {
}
