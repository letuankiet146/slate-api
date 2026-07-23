package com.slatevn.service;



import com.slatevn.domain.Membership;

import com.slatevn.domain.PermissionCodes;

import com.slatevn.domain.Role;

import com.slatevn.domain.RoleCodes;

import com.slatevn.domain.ScopeType;

import com.slatevn.domain.User;

import com.slatevn.domain.Workspace;

import com.slatevn.dto.CreateUserRequest;

import com.slatevn.dto.UpdateUserRequest;

import com.slatevn.dto.UserDto;

import com.slatevn.dto.UserMembershipDto;

import com.slatevn.repository.BoardRepository;

import com.slatevn.repository.MembershipRepository;

import com.slatevn.repository.RefreshTokenRepository;

import com.slatevn.repository.RoleRepository;

import com.slatevn.repository.TaskRepository;

import com.slatevn.repository.UserRepository;

import com.slatevn.repository.WorkspaceJoinRequestRepository;

import com.slatevn.repository.WorkspaceRepository;

import com.slatevn.web.BadRequestException;

import com.slatevn.web.NotFoundException;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import java.time.Instant;

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

    private final BoardService boardService;

    private final TaskRepository taskRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final WorkspaceJoinRequestRepository joinRequestRepository;

    private final AuthorizationService authorizationService;

    private final PasswordEncoder passwordEncoder;



    public UserService(

            UserRepository userRepository,

            RoleRepository roleRepository,

            MembershipRepository membershipRepository,

            WorkspaceRepository workspaceRepository,

            BoardRepository boardRepository,

            BoardService boardService,

            TaskRepository taskRepository,

            RefreshTokenRepository refreshTokenRepository,

            WorkspaceJoinRequestRepository joinRequestRepository,

            AuthorizationService authorizationService,

            PasswordEncoder passwordEncoder

    ) {

        this.userRepository = userRepository;

        this.roleRepository = roleRepository;

        this.membershipRepository = membershipRepository;

        this.workspaceRepository = workspaceRepository;

        this.boardRepository = boardRepository;

        this.boardService = boardService;

        this.taskRepository = taskRepository;

        this.refreshTokenRepository = refreshTokenRepository;

        this.joinRequestRepository = joinRequestRepository;

        this.authorizationService = authorizationService;

        this.passwordEncoder = passwordEncoder;

    }



    @Transactional(readOnly = true)

    public List<UserDto> list(UUID actorId) {

        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);

        return userRepository.findByDeletedAtIsNullOrderByDisplayNameAsc().stream().map(this::toDto).toList();

    }



    @Transactional(readOnly = true)

    public List<UserDto> listDeleted(UUID actorId) {

        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);

        return userRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc().stream().map(this::toDto).toList();

    }



    @Transactional(readOnly = true)

    public UserDto get(UUID actorId, UUID id) {

        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);

        return toDto(requireUser(id));

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

        user.setAccountType(com.slatevn.domain.AccountType.OWNER);

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

        User user = requireActiveUser(id);



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

            boolean wasSystemAdmin = membershipRepository.findByUserIdAndScopeType(user.getId(), ScopeType.SYSTEM).stream()

                    .anyMatch(m -> RoleCodes.SYSTEM_ADMIN.equals(m.getRole().getCode()));

            boolean willBeSystemAdmin = request.systemRoleCodes().contains(RoleCodes.SYSTEM_ADMIN);

            if (wasSystemAdmin && !willBeSystemAdmin

                    && membershipRepository.countByScopeTypeAndRole_Code(ScopeType.SYSTEM, RoleCodes.SYSTEM_ADMIN) <= 1) {

                throw new BadRequestException("Cannot remove the last system administrator");

            }

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

        User user = requireActiveUser(id);

        assertCanDeleteUser(id);

        softDeleteUserCascade(actorId, user);

    }



    @Transactional

    public UserDto restore(UUID actorId, UUID id) {

        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);

        User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));

        if (!user.isDeleted()) {

            throw new BadRequestException("User is not deleted");

        }

        user.setDeletedAt(null);

        user.setDeletedBy(null);

        user.setEnabled(true);

        userRepository.save(user);



        for (Workspace workspace : workspaceRepository.findByOwnerId(id)) {

            if (workspace.isDeleted()) {

                boardService.restoreBoardsInWorkspace(workspace.getId());

                workspace.setDeletedAt(null);

                workspace.setDeletedBy(null);

                workspaceRepository.save(workspace);

            }

        }



        for (User internal : userRepository.findByCreatedByUserId(id)) {

            if (internal.isDeleted()) {

                internal.setDeletedAt(null);

                internal.setDeletedBy(null);

                internal.setEnabled(true);

                userRepository.save(internal);

            }

        }



        return toDto(user);

    }



    @Transactional

    public void permanentDelete(UUID actorId, UUID id) {

        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);

        if (actorId.equals(id)) {

            throw new BadRequestException("Cannot permanently delete your own account");

        }

        User user = requireUser(id);

        if (!user.isDeleted()) {

            throw new BadRequestException("Only deleted users can be permanently removed");

        }

        assertCanDeleteUser(id);



        for (User internal : userRepository.findByCreatedByUserId(id)) {

            if (internal.isDeleted()) {

                permanentDelete(actorId, internal.getId());

            }

        }



        for (Workspace workspace : workspaceRepository.findByOwnerId(id)) {

            workspaceRepository.delete(workspace);

        }



        clearRemainingUserReferences(actorId, id);

        userRepository.delete(user);

    }



    private void softDeleteUserCascade(UUID actorId, User user) {

        UUID id = user.getId();

        Instant now = Instant.now();



        for (User internal : userRepository.findByCreatedByUserId(id)) {

            if (!internal.isDeleted()) {

                softDeleteUserCascade(actorId, internal);

            }

        }



        for (Workspace workspace : workspaceRepository.findByOwnerId(id)) {

            if (!workspace.isDeleted()) {

                boardService.softDeleteBoardsInWorkspace(actorId, workspace.getId(), now);

                workspace.setDeletedAt(now);

                workspace.setDeletedBy(actorId);

                workspaceRepository.save(workspace);

            }

        }



        taskRepository.findByAssigneeId(id).forEach(task -> task.setAssigneeId(null));

        membershipRepository.deleteAll(membershipRepository.findByUserId(id));

        refreshTokenRepository.deleteByUserId(id);



        user.setDeletedAt(now);

        user.setDeletedBy(actorId);

        user.setEnabled(false);

        userRepository.save(user);

    }



    private void clearRemainingUserReferences(UUID actorId, UUID id) {

        workspaceRepository.findByCreatedBy(id).forEach(workspace -> {

            workspace.setCreatedBy(actorId);

            workspaceRepository.save(workspace);

        });

        boardRepository.findByCreatedBy(id).forEach(board -> {

            board.setCreatedBy(actorId);

            boardRepository.save(board);

        });

        taskRepository.findByCreatedBy(id).forEach(task -> {

            task.setCreatedBy(actorId);

            taskRepository.save(task);

        });

        taskRepository.findByAssigneeId(id).forEach(task -> {

            task.setAssigneeId(null);

            taskRepository.save(task);

        });

        workspaceRepository.findByDeletedBy(id).forEach(workspace -> {

            workspace.setDeletedBy(null);

            workspaceRepository.save(workspace);

        });

        boardRepository.findByDeletedBy(id).forEach(board -> {

            board.setDeletedBy(null);

            boardRepository.save(board);

        });

        taskRepository.findByDeletedBy(id).forEach(task -> {

            task.setDeletedBy(null);

            taskRepository.save(task);

        });

        userRepository.findByDeletedBy(id).forEach(other -> {

            other.setDeletedBy(null);

            userRepository.save(other);

        });

        userRepository.findByCreatedByUserId(id).forEach(other -> {

            other.setCreatedByUserId(null);

            userRepository.save(other);

        });

        joinRequestRepository.findByReviewedBy(id).forEach(request -> {

            request.setReviewedBy(null);

            joinRequestRepository.save(request);

        });

        membershipRepository.deleteAll(membershipRepository.findByUserId(id));

        refreshTokenRepository.deleteByUserId(id);

    }



    private void assertCanDeleteUser(UUID id) {

        boolean isSystemAdmin = membershipRepository.findByUserIdAndScopeType(id, ScopeType.SYSTEM).stream()

                .anyMatch(m -> RoleCodes.SYSTEM_ADMIN.equals(m.getRole().getCode()));

        if (isSystemAdmin

                && membershipRepository.countByScopeTypeAndRole_Code(ScopeType.SYSTEM, RoleCodes.SYSTEM_ADMIN) <= 1) {

            throw new BadRequestException("Cannot delete the last system administrator");

        }

    }



    private User requireUser(UUID id) {

        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));

    }



    private User requireActiveUser(UUID id) {

        User user = requireUser(id);

        if (user.isDeleted()) {

            throw new BadRequestException("User is deleted");

        }

        return user;

    }



    private void assignSystemRoles(User user, List<String> roleCodes) {

        for (String code : roleCodes) {

            if (!RoleCodes.SYSTEM_ADMIN.equals(code)) {

                throw new BadRequestException("Only SYSTEM_ADMIN can be assigned at system scope");

            }

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

        List<UserMembershipDto> memberships = membershipRepository.findByUserId(user.getId()).stream()

                .filter(m -> m.getScopeType() != ScopeType.SYSTEM && m.getWorkspaceId() != null)

                .map(m -> {

                    String workspaceName = workspaceRepository.findById(m.getWorkspaceId())

                            .map(Workspace::getName)

                            .orElse(m.getWorkspaceId().toString());

                    return new UserMembershipDto(

                            m.getId(),

                            m.getWorkspaceId(),

                            workspaceName,

                            m.getRole().getCode(),

                            m.getRole().getName()

                    );

                })

                .toList();

        return new UserDto(

                user.getId(),

                user.getEmail(),

                user.getDisplayName(),

                user.getAvatarUrl(),

                user.getLocale(),

                user.isEnabled(),

                new ArrayList<>(roles),

                memberships,

                user.getCreatedAt(),

                user.getDeletedAt()

        );

    }

}

