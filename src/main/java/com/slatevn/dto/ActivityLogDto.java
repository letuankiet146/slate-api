package com.slatevn.dto;

import java.time.Instant;
import java.util.UUID;

public record ActivityLogDto(
        UUID id,
        UUID workspaceId,
        String scopeLevel,
        UUID boardId,
        UUID taskId,
        UUID actorId,
        String actorName,
        String action,
        String entityType,
        UUID entityId,
        String summary,
        String details,
        Instant createdAt
) {
}
