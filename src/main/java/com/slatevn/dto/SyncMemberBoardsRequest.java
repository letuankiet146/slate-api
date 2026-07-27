package com.slatevn.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record SyncMemberBoardsRequest(
        @NotBlank String roleCode,
        List<UUID> boardIds
) {
}
