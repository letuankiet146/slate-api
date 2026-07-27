package com.slatevn.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MyTasksTaskDto(
        UUID id,
        UUID boardId,
        String boardName,
        UUID columnId,
        String columnName,
        String title,
        String description,
        UUID assigneeId,
        UUID templateId,
        String templateName,
        int position,
        List<TaskFieldValueDto> fieldValues,
        Instant createdAt,
        Instant updatedAt
) {
}
