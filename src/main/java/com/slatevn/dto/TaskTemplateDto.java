package com.slatevn.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskTemplateDto(
        UUID id,
        UUID workspaceId,
        String name,
        List<FieldDefinitionDto> fields,
        List<UUID> visibleBoardIds,
        Instant createdAt
) {
}
