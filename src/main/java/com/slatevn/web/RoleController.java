package com.slatevn.web;

import com.slatevn.domain.Role;
import com.slatevn.repository.RoleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return roleRepository.findAll().stream()
                .map(this::toMap)
                .toList();
    }

    private Map<String, Object> toMap(Role role) {
        return Map.of(
                "id", role.getId(),
                "code", role.getCode(),
                "name", role.getName(),
                "description", role.getDescription() == null ? "" : role.getDescription(),
                "permissions", role.getPermissions().stream().map(p -> p.getCode()).sorted().toList()
        );
    }
}
