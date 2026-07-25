package com.slatevn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameColumnRequest(
        @NotBlank @Size(max = 128) String name
) {
}
