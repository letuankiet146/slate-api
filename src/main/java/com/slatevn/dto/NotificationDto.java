package com.slatevn.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        String type,
        UUID referenceId,
        String title,
        String body,
        boolean read,
        Instant createdAt,
        WorkspaceJoinRequestDto joinRequest
) {
}
