package com.slatevn.dto;

import java.util.UUID;

public record TaskNotificationContextDto(
        UUID taskId,
        UUID boardId,
        String taskTitle
) {
}
