package com.slatevn.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskDto(
        UUID id,
        UUID boardId,
        UUID columnId,
        String title,
        String description,
        UUID createdBy,
        UUID assigneeId,
        UUID templateId,
        String templateName,
        int position,
        List<TaskFieldValueDto> fields,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
}
