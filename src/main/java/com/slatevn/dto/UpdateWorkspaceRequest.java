package com.slatevn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWorkspaceRequest(
        @NotBlank @Size(max = 255) String name
) {
}
