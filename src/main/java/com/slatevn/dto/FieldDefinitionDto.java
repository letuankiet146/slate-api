package com.slatevn.dto;

import com.slatevn.domain.FieldType;
import com.slatevn.domain.FieldVisibility;

import java.util.List;
import java.util.UUID;

public record FieldDefinitionDto(
        UUID id,
        UUID boardId,
        UUID taskId,
        UUID templateId,
        String name,
        FieldType fieldType,
        boolean required,
        boolean editable,
        FieldVisibility visibility,
        int position,
        List<String> requiredColumnNames
) {
}
