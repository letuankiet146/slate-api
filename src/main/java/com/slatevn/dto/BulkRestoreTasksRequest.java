package com.slatevn.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BulkRestoreTasksRequest(
        @NotEmpty List<UUID> taskIds,
        @NotNull UUID boardId
) {
}
