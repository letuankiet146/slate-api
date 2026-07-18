package com.slatevn.service;

import com.slatevn.domain.Permission;
import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.Role;
import com.slatevn.domain.RoleCodes;
import com.slatevn.dto.CreateRoleRequest;
import com.slatevn.dto.UpdateRoleRequest;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.PermissionRepository;
import com.slatevn.repository.RoleRepository;
import com.slatevn.web.BadRequestException;
import com.slatevn.web.ForbiddenException;
import com.slatevn.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RoleService {

    private static final Set<String> BUILT_IN_ROLE_CODES = Set.of(
            RoleCodes.SYSTEM_ADMIN,
            RoleCodes.WORKSPACE_ADMIN,
            "BOARD_MEMBER",
            RoleCodes.BOARD_VIEWER
    );

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MembershipRepository membershipRepository;
    private final AuthorizationService authorizationService;

    public RoleService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            MembershipRepository membershipRepository,
            AuthorizationService authorizationService
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.membershipRepository = membershipRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRoles() {
        return roleRepository.findAll().stream()
                .map(this::toMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPermissions(UUID actorId) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        return permissionRepository.findAll().stream()
                .filter(p -> !PermissionCodes.USER_MANAGE.equals(p.getCode()))
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "code", p.getCode(),
                        "description", p.getDescription()
                ))
                .toList();
    }

    @Transactional
    public Map<String, Object> create(UUID actorId, CreateRoleRequest request) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        if (roleRepository.findByCode(request.code()).isPresent()) {
            throw new BadRequestException("Role code already exists: " + request.code());
        }
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(resolvePermissions(request.permissionCodes()));
        return toMap(roleRepository.save(role));
    }

    @Transactional
    public Map<String, Object> update(UUID actorId, UUID roleId, UpdateRoleRequest request) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found"));
        if (RoleCodes.SYSTEM_ADMIN.equals(role.getCode())) {
            throw new ForbiddenException("Cannot modify the SYSTEM_ADMIN role");
        }
        if (request.name() != null && !request.name().isBlank()) {
            role.setName(request.name());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        if (request.permissionCodes() != null) {
            role.setPermissions(resolvePermissions(request.permissionCodes()));
        }
        return toMap(roleRepository.save(role));
    }

    @Transactional
    public void delete(UUID actorId, UUID roleId) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found"));
        if (BUILT_IN_ROLE_CODES.contains(role.getCode())) {
            throw new BadRequestException("Cannot delete built-in role: " + role.getCode());
        }
        if (membershipRepository.existsByRole_Id(roleId)) {
            throw new BadRequestException("Role is assigned to users and cannot be deleted");
        }
        roleRepository.delete(role);
    }

    private Set<Permission> resolvePermissions(List<String> permissionCodes) {
        Set<Permission> permissions = new HashSet<>();
        for (String code : permissionCodes) {
            Permission permission = permissionRepository.findByCode(code)
                    .orElseThrow(() -> new BadRequestException("Unknown permission: " + code));
            permissions.add(permission);
        }
        return permissions;
    }

    private Map<String, Object> toMap(Role role) {
        return Map.of(
                "id", role.getId(),
                "code", role.getCode(),
                "name", role.getName(),
                "description", role.getDescription() == null ? "" : role.getDescription(),
                "permissions", role.getPermissions().stream().map(Permission::getCode).sorted().toList(),
                "builtIn", BUILT_IN_ROLE_CODES.contains(role.getCode())
        );
    }
}
