package com.slatevn.web;

import com.slatevn.dto.CreateRoleRequest;
import com.slatevn.dto.UpdateRoleRequest;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> listRoles() {
        return roleService.listRoles();
    }

    @GetMapping("/permissions")
    public List<Map<String, Object>> listPermissions() {
        return roleService.listPermissions(SecurityUtils.currentUser().getId());
    }

    @PostMapping("/roles")
    public Map<String, Object> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return roleService.create(SecurityUtils.currentUser().getId(), request);
    }

    @PatchMapping("/roles/{id}")
    public Map<String, Object> updateRole(
            @PathVariable UUID id,
            @RequestBody UpdateRoleRequest request
    ) {
        return roleService.update(SecurityUtils.currentUser().getId(), id, request);
    }

    @DeleteMapping("/roles/{id}")
    public void deleteRole(@PathVariable UUID id) {
        roleService.delete(SecurityUtils.currentUser().getId(), id);
    }
}
