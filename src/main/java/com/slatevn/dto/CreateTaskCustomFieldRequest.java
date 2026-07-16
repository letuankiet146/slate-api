package com.slatevn.dto;

import com.slatevn.domain.FieldType;
import com.slatevn.domain.FieldVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateTaskCustomFieldRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull FieldType fieldType,
        boolean required,
        boolean editable,
        FieldVisibility visibility,
        List<UUID> requiredColumnIds,
        String value
) {
}
