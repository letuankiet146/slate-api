package com.slatevn.dto;

import com.slatevn.domain.FieldType;
import com.slatevn.domain.FieldVisibility;

import java.util.List;
import java.util.UUID;

public record TaskFieldValueDto(
        UUID fieldDefinitionId,
        String name,
        FieldType fieldType,
        boolean required,
        boolean editable,
        FieldVisibility visibility,
        String value,
        List<String> requiredColumnNames,
        boolean taskSpecific
) {
}
