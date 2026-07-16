package com.slatevn.service;

import com.slatevn.domain.Membership;
import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.Role;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.User;
import com.slatevn.dto.CreateUserRequest;
import com.slatevn.dto.UpdateUserRequest;
import com.slatevn.dto.UserDto;
import com.slatevn.repository.BoardRepository;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.RoleRepository;
import com.slatevn.repository.TaskRepository;
import com.slatevn.repository.UserRepository;
import com.slatevn.repository.WorkspaceRepository;
import com.slatevn.web.BadRequestException;
import com.slatevn.web.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MembershipRepository membershipRepository;
    private final WorkspaceRepository workspaceRepository;
    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final AuthorizationService authorizationService;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            MembershipRepository membershipRepository,
            WorkspaceRepository workspaceRepository,
            BoardRepository boardRepository,
            TaskRepository taskRepository,
            AuthorizationService authorizationService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.workspaceRepository = workspaceRepository;
        this.boardRepository = boardRepository;
        this.taskRepository = taskRepository;
        this.authorizationService = authorizationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list(UUID actorId) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        return userRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID actorId, UUID id) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        return toDto(userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found")));
    }

    @Transactional
    public UserDto create(UUID actorId, CreateUserRequest request) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("Email already exists");
        }
        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setLocale(request.locale() != null && !request.locale().isBlank() ? request.locale() : "vi");
        user.setEnabled(true);
        userRepository.save(user);

        List<String> roleCodes = request.systemRoleCodes() == null || request.systemRoleCodes().isEmpty()
                ? List.of()
                : request.systemRoleCodes();
        assignSystemRoles(user, roleCodes);

        return toDto(user);
    }

    @Transactional
    public UserDto update(UUID actorId, UUID id, UpdateUserRequest request) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName());
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            user.setLocale(request.locale());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.systemRoleCodes() != null) {
            membershipRepository.findByUserIdAndScopeType(user.getId(), ScopeType.SYSTEM)
                    .forEach(membershipRepository::delete);
            assignSystemRoles(user, request.systemRoleCodes());
        }
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID actorId, UUID id) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        if (actorId.equals(id)) {
            throw new BadRequestException("Cannot delete your own account");
        }
        User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        boolean isSystemAdmin = membershipRepository.findByUserIdAndScopeType(id, ScopeType.SYSTEM).stream()
                .anyMatch(m -> "SYSTEM_ADMIN".equals(m.getRole().getCode()));
        if (isSystemAdmin
                && membershipRepository.countByScopeTypeAndRole_Code(ScopeType.SYSTEM, "SYSTEM_ADMIN") <= 1) {
            throw new BadRequestException("Cannot delete the last system administrator");
        }

        workspaceRepository.findByCreatedBy(id).forEach(workspace -> workspace.setCreatedBy(actorId));
        boardRepository.findByCreatedBy(id).forEach(board -> board.setCreatedBy(actorId));
        taskRepository.findByCreatedBy(id).forEach(task -> task.setCreatedBy(actorId));
        taskRepository.findByAssigneeId(id).forEach(task -> task.setAssigneeId(null));
        workspaceRepository.findByDeletedBy(id).forEach(workspace -> workspace.setDeletedBy(null));
        boardRepository.findByDeletedBy(id).forEach(board -> board.setDeletedBy(null));
        taskRepository.findByDeletedBy(id).forEach(task -> task.setDeletedBy(null));

        userRepository.delete(user);
    }

    private void assignSystemRoles(User user, List<String> roleCodes) {
        for (String code : roleCodes) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new BadRequestException("Unknown role: " + code));
            Membership membership = new Membership();
            membership.setUser(user);
            membership.setRole(role);
            membership.setScopeType(ScopeType.SYSTEM);
            membershipRepository.save(membership);
        }
    }

    private UserDto toDto(User user) {
        List<String> roles = membershipRepository.findByUserIdAndScopeType(user.getId(), ScopeType.SYSTEM).stream()
                .map(m -> m.getRole().getCode())
                .toList();
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getLocale(),
                user.isEnabled(),
                new ArrayList<>(roles),
                user.getCreatedAt()
        );
    }
}
