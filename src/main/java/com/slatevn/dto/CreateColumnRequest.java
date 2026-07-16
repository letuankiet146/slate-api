package com.slatevn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateColumnRequest(
        @NotBlank @Size(max = 128) String name,
        Integer position
) {
}
