package com.slatevn.dto;

import jakarta.validation.constraints.NotBlank;

public record MyTasksMoveRequest(
        @NotBlank String columnName,
        Integer position
) {
}
