package com.slatevn.dto;

import java.util.List;

public record UpdateRoleRequest(
        String name,
        String description,
        List<String> permissionCodes
) {
}
