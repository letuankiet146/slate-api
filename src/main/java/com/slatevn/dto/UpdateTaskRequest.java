package com.slatevn.dto;

import java.util.Map;
import java.util.UUID;

public record UpdateTaskRequest(
        String title,
        String description,
        UUID assigneeId,
        Map<UUID, String> fieldValues
) {
}
