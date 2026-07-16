package com.slatevn.dto;

import java.util.List;

public record UpdateUserRequest(
        String displayName,
        String locale,
        Boolean enabled,
        String password,
        List<String> systemRoleCodes
) {
}
