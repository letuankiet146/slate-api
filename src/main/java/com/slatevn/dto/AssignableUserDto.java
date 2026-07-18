package com.slatevn.dto;

import java.util.UUID;

public record AssignableUserDto(
        UUID id,
        String email,
        String displayName
) {
}
