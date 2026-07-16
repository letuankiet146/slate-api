package com.slatevn.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SaveTaskTemplateRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull @Valid List<TemplateFieldRequest> fields,
        @NotNull List<UUID> visibleBoardIds
) {
}
