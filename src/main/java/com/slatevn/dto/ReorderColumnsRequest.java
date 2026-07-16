package com.slatevn.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderColumnsRequest(
        @NotEmpty List<UUID> columnIds
) {
}
