package com.slatevn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskCommentRequest(
        @NotBlank @Size(max = 10000) String body
) {
}
