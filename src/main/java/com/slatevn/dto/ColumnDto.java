package com.slatevn.dto;

import java.util.UUID;

public record ColumnDto(
        UUID id,
        UUID boardId,
        String name,
        int position
) {
}
