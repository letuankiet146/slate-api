package com.slatevn.dto;

public record RegisterResponse(
        String message,
        boolean joinRequestSent
) {
}
