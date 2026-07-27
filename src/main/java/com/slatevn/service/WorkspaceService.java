package com.slatevn.service;

import com.slatevn.domain.ActivityAction;
import com.slatevn.domain.ActivityEntityType;
import com.slatevn.domain.ActivityScopeLevel;
import com.slatevn.domain.Membership;
import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.Role;
import com.slatevn.domain.RoleCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.User;
import com.slatevn.domain.Board;
import com.slatevn.domain.Workspace;
import com.slatevn.dto.AddMembershipRequest;
import com.slatevn.dto.AssignableUserDto;
import com.slatevn.dto.CreateInternalUserRequest;
import com.slatevn.dto.CreateWorkspaceRequest;
import com.slatevn.dto.MembershipDto;
import com.slatevn.dto.SyncMemberBoardsRequest;
import com.slatevn.dto.UpdateMembershipRequest;
import com.slatevn.dto.UpdateWorkspaceRequest;
import com.slatevn.dto.WorkspaceDetailDto;
import com.slatevn.dto.WorkspaceDto;
import com.slatevn.repository.BoardRepository;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.RoleRepository;
import com.slatevn.repository.UserRepository;
import com.slatevn.repository.WorkspaceRepository;
import com.slatevn.util.WorkspaceKeyGenerator;
import com.slatevn.web.BadRequestException;
import com.slatevn.web.ForbiddenException;
import com.slatevn.web.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final MembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final BoardService boardService;
    private final AuthorizationService authorizationService;
    private final TaskTemplateService taskTemplateService;
    private final ActivityLogService activityLogService;
    private final PasswordEncoder passwordEncoder;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            MembershipRepository membershipRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            BoardRepository boardRepository,
            BoardService boardService,
            AuthorizationService authorizationService,
            TaskTemplateService taskTemplateService,
            ActivityLogService activityLogService,
            PasswordEncoder passwordEncoder
    ) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.boardRepository = boardRepository;
        this.boardService = boardService;
        this.authorizationService = authorizationService;
        this.taskTemplateService = taskTemplateService;
        this.activityLogService = activityLogService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> list(UUID actorId) {
        if (authorizationService.isSystemAdmin(actorId)) {
            return workspaceRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                    .map(this::toDto)
                    .toList();
        }
        return authorizationService.workspaceAdminWorkspaceIds(actorId).stream()
                .map(workspaceRepository::findById)
                .flatMap(Optional::stream)
                .filter(w -> !w.isDeleted())
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceDetailDto getDetail(UUID actorId, UUID id) {
        Workspace workspace = requireActiveWorkspace(id);
        requireCanViewWorkspace(actorId, id);
        Set<String> permissions = authorizationService.resolveWorkspacePermissions(actorId, id);
        return toDetailDto(workspace, permissions, authorizationService.isWorkspaceAdmin(actorId, id));
    }

    @Transactional(readOnly = true)
    public WorkspaceDto get(UUID actorId, UUID id) {
        Workspace workspace = requireActiveWorkspace(id);
        requireCanViewWorkspace(actorId, id);
        return toDto(workspace);
    }

    @Transactional
    public WorkspaceDto update(UUID actorId, UUID workspaceId, UpdateWorkspaceRequest request) {
        authorizationService.requireWorkspacePermission(actorId, workspaceId, PermissionCodes.WORKSPACE_MANAGE);
        Workspace workspace = requireActiveWorkspace(workspaceId);
        String oldName = workspace.getName();
        workspace.setName(request.name().trim());
        workspaceRepository.save(workspace);

        if (!oldName.equals(workspace.getName())) {
            activityLogService.log(
                    workspaceId,
                    ActivityScopeLevel.WORKSPACE,
                    null,
                    null,
                    actorId,
                    ActivityAction.UPDATE,
                    ActivityEntityType.WORKSPACE,
                    workspaceId,
                    "Renamed workspace \"" + oldName + "\" to \"" + workspace.getName() + "\"",
                    null
            );
        }

        return toDto(workspace);
    }

    @Transactional
    public void softDelete(UUID actorId, UUID workspaceId) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        Workspace workspace = requireActiveWorkspace(workspaceId);
        Instant now = Instant.now();
        boardService.softDeleteBoardsInWorkspace(actorId, workspaceId, now);
        workspace.setDeletedAt(now);
        workspace.setDeletedBy(actorId);
        workspaceRepository.save(workspace);

        activityLogService.log(
                workspaceId,
                ActivityScopeLevel.WORKSPACE,
                null,
                null,
                actorId,
                ActivityAction.DELETE,
                ActivityEntityType.WORKSPACE,
                workspaceId,
                "Deleted workspace \"" + workspace.getName() + "\"",
                null
        );
    }

    @Transactional
    public WorkspaceDto restore(UUID actorId, UUID workspaceId) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found"));
        if (!workspace.isDeleted()) {
            throw new BadRequestException("Workspace is not deleted");
        }
        workspace.setDeletedAt(null);
        workspace.setDeletedBy(null);
        workspaceRepository.save(workspace);

        boardService.restoreBoardsInWorkspace(workspaceId);

        activityLogService.log(
                workspaceId,
                ActivityScopeLevel.WORKSPACE,
                null,
                null,
                actorId,
                ActivityAction.RESTORE,
                ActivityEntityType.WORKSPACE,
                workspaceId,
                "Restored workspace \"" + workspace.getName() + "\"",
                null
        );

        return toDto(workspace);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> listDeleted(UUID actorId) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        return workspaceRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasAnyWorkspaceAdminRole(UUID actorId) {
        return authorizationService.hasAnyWorkspaceAdminRole(actorId);
    }

    @Transactional(readOnly = true)
    public boolean canCreateWorkspace(UUID actorId) {
        return authorizationService.hasSystemPermission(actorId, PermissionCodes.USER_MANAGE);
    }

    @Transactional
    public WorkspaceDto create(UUID actorId, CreateWorkspaceRequest request) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);

        String key = request.key() == null ? "" : request.key().trim();
        if (key.isEmpty()) {
            key = WorkspaceKeyGenerator.generateUniqueKey(request.name(), workspaceRepository);
        } else {
            if (!WorkspaceKeyGenerator.isValidKey(key)) {
                throw new BadRequestException("Invalid workspace key");
            }
            key = key.toUpperCase();
            if (workspaceRepository.existsByKeyIgnoreCase(key)) {
                throw new BadRequestException("Workspace key already exists");
            }
        }

        User adminUser = resolveOrCreateWorkspaceAdmin(actorId, request);

        Workspace workspace = new Workspace();
        workspace.setName(request.name().trim());
        workspace.setKey(key);
        workspace.setCreatedBy(actorId);
        workspace.setOwnerId(adminUser.getId());
        workspaceRepository.save(workspace);
        taskTemplateService.ensureDefaultTemplate(workspace.getId());

        assignWorkspaceAdmin(adminUser, workspace.getId());

        return toDto(workspace);
    }

    private User resolveOrCreateWorkspaceAdmin(UUID actorId, CreateWorkspaceRequest request) {
        String email = request.adminEmail().trim().toLowerCase();
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.isDeleted() || !user.isEnabled()) {
                throw new BadRequestException("Workspace admin user is not available");
            }
            if (authorizationService.isSystemAdmin(user.getId())) {
                throw new BadRequestException("System administrators cannot be workspace administrators");
            }
            return user;
        }

        String displayName = request.adminDisplayName() == null ? "" : request.adminDisplayName().trim();
        String password = request.adminPassword() == null ? "" : request.adminPassword();
        if (displayName.isEmpty()) {
            throw new BadRequestException("Display name is required for new workspace admin");
        }
        if (password.length() < 6) {
            throw new BadRequestException("Temporary password must be at least 6 characters");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setLocale("vi");
        user.setEnabled(true);
        user.setMustChangePassword(true);
        user.setCreatedByUserId(actorId);
        return userRepository.save(user);
    }

    private void assignWorkspaceAdmin(User user, UUID workspaceId) {
        Role adminRole = roleRepository.findByCode(RoleCodes.WORKSPACE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("WORKSPACE_ADMIN missing"));
        Membership membership = new Membership();
        membership.setUser(user);
        membership.setRole(adminRole);
        membership.setScopeType(ScopeType.WORKSPACE);
        membership.setWorkspaceId(workspaceId);
        membershipRepository.save(membership);
    }

    @Transactional
    public List<MembershipDto> createInternalUser(UUID actorId, UUID workspaceId, CreateInternalUserRequest request) {
        requireCanManageWorkspaceMembers(actorId, workspaceId);
        requireActiveWorkspace(workspaceId);

        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("Email already exists — add the existing user instead");
        }
        validateMemberRole(request.roleCode());

        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setLocale("vi");
        user.setEnabled(true);
        user.setMustChangePassword(true);
        user.setCreatedByUserId(actorId);
        userRepository.save(user);

        AddMembershipRequest membershipRequest = new AddMembershipRequest(
                request.email(),
                request.roleCode(),
                request.scopeType(),
                request.boardId(),
                request.boardIds()
        );
        return addMemberships(actorId, workspaceId, membershipRequest);
    }

    @Transactional(readOnly = true)
    public List<MembershipDto> listMemberships(UUID actorId, UUID workspaceId) {
        authorizationService.requireWorkspaceAdmin(actorId, workspaceId);
        return membershipRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::toMembershipDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssignableUserDto lookupMemberByEmail(UUID actorId, UUID workspaceId, String email) {
        requireCanManageWorkspaceMembers(actorId, workspaceId);
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (!user.isEnabled() || user.isDeleted()) {
            throw new BadRequestException("User is disabled");
        }
        if (authorizationService.isSystemAdmin(user.getId())) {
            throw new NotFoundException("User not found");
        }
        return new AssignableUserDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl());
    }

    @Transactional
    public MembershipDto addMembership(UUID actorId, UUID workspaceId, AddMembershipRequest request) {
        List<MembershipDto> created = addMemberships(actorId, workspaceId, request);
        return created.getFirst();
    }

    @Transactional
    public List<MembershipDto> addMemberships(UUID actorId, UUID workspaceId, AddMembershipRequest request) {
        requireCanManageWorkspaceMembers(actorId, workspaceId);
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new NotFoundException("Workspace not found");
        }
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (!user.isEnabled() || user.isDeleted()) {
            throw new BadRequestException("User is disabled");
        }
        if (authorizationService.isSystemAdmin(user.getId())) {
            throw new BadRequestException("System administrators cannot be added as workspace members");
        }
        validateMemberRole(request.roleCode());
        Role role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new BadRequestException("Unknown role: " + request.roleCode()));

        List<UUID> boardIds = resolveBoardIds(request);
        if (authorizationService.isTaskDerivedBoardRole(role.getCode())) {
            if (hasWorkspaceTaskDerivedMembership(user.getId(), workspaceId, role.getCode())) {
                throw new BadRequestException("User already has role " + role.getCode() + " in this workspace");
            }
        } else if (authorizationService.requiresManualBoardAssignment(role.getCode())) {
            if (boardIds.isEmpty()) {
                throw new BadRequestException("At least one board is required for role " + role.getCode());
            }
            for (UUID boardId : boardIds) {
                if (hasBoardMembership(user.getId(), workspaceId, boardId, role.getCode())) {
                    throw new BadRequestException("User already has role " + role.getCode() + " on this board");
                }
            }
        } else if (RoleCodes.WORKSPACE_ADMIN.equals(role.getCode())) {
            if (hasWorkspaceAdminMembership(user.getId(), workspaceId)) {
                throw new BadRequestException("User is already a workspace administrator");
            }
        }

        List<MembershipDto> savedMemberships = new ArrayList<>();
        if (authorizationService.isTaskDerivedBoardRole(role.getCode())) {
            Membership membership = new Membership();
            membership.setUser(user);
            membership.setRole(role);
            membership.setScopeType(ScopeType.WORKSPACE);
            membership.setWorkspaceId(workspaceId);
            MembershipDto saved = toMembershipDto(membershipRepository.save(membership));
            logMembershipAdded(actorId, workspaceId, saved);
            savedMemberships.add(saved);
        } else if (authorizationService.requiresManualBoardAssignment(role.getCode())) {
            for (UUID boardId : boardIds) {
                savedMemberships.add(saveBoardMembership(actorId, workspaceId, user, role, boardId));
            }
        } else {
            Membership membership = new Membership();
            membership.setUser(user);
            membership.setRole(role);
            membership.setScopeType(ScopeType.WORKSPACE);
            membership.setWorkspaceId(workspaceId);
            MembershipDto saved = toMembershipDto(membershipRepository.save(membership));
            logMembershipAdded(actorId, workspaceId, saved);
            savedMemberships.add(saved);
        }
        return savedMemberships;
    }

    private MembershipDto saveBoardMembership(
            UUID actorId,
            UUID workspaceId,
            User user,
            Role role,
            UUID boardId
    ) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        if (!board.getWorkspaceId().equals(workspaceId)) {
            throw new BadRequestException("Board does not belong to workspace");
        }
        Membership membership = new Membership();
        membership.setUser(user);
        membership.setRole(role);
        membership.setScopeType(ScopeType.BOARD);
        membership.setBoardId(board.getId());
        membership.setWorkspaceId(workspaceId);
        MembershipDto saved = toMembershipDto(membershipRepository.save(membership));
        logMembershipAdded(actorId, workspaceId, saved);
        return saved;
    }

    private void logMembershipAdded(UUID actorId, UUID workspaceId, MembershipDto saved) {
        activityLogService.log(
                workspaceId,
                ActivityScopeLevel.WORKSPACE,
                null,
                null,
                actorId,
                ActivityAction.CREATE,
                ActivityEntityType.MEMBERSHIP,
                saved.id(),
                "Added member " + saved.userDisplayName() + " (" + saved.roleCode() + ")",
                null
        );
    }

    private void validateMemberRole(String roleCode) {
        if (RoleCodes.SYSTEM_ADMIN.equals(roleCode)) {
            throw new BadRequestException("Invalid role");
        }
    }

    private List<UUID> resolveBoardIds(AddMembershipRequest request) {
        if (request.boardIds() != null && !request.boardIds().isEmpty()) {
            return request.boardIds();
        }
        if (request.boardId() != null) {
            return List.of(request.boardId());
        }
        return List.of();
    }

    private boolean hasWorkspaceTaskDerivedMembership(UUID userId, UUID workspaceId, String roleCode) {
        return membershipRepository.findByWorkspaceId(workspaceId).stream()
                .anyMatch(m -> userId.equals(m.getUser().getId())
                        && m.getScopeType() == ScopeType.WORKSPACE
                        && roleCode.equals(m.getRole().getCode()));
    }

    private boolean hasWorkspaceAdminMembership(UUID userId, UUID workspaceId) {
        return membershipRepository.findByWorkspaceId(workspaceId).stream()
                .anyMatch(m -> userId.equals(m.getUser().getId())
                        && m.getScopeType() == ScopeType.WORKSPACE
                        && RoleCodes.WORKSPACE_ADMIN.equals(m.getRole().getCode()));
    }

    private boolean hasBoardMembership(UUID userId, UUID workspaceId, UUID boardId, String roleCode) {
        return membershipRepository.findByWorkspaceId(workspaceId).stream()
                .anyMatch(m -> userId.equals(m.getUser().getId())
                        && m.getScopeType() == ScopeType.BOARD
                        && boardId.equals(m.getBoardId())
                        && roleCode.equals(m.getRole().getCode()));
    }

    @Transactional
    public MembershipDto updateMembership(
            UUID actorId,
            UUID workspaceId,
            UUID membershipId,
            UpdateMembershipRequest request
    ) {
        requireCanManageWorkspaceMembers(actorId, workspaceId);
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NotFoundException("Membership not found"));
        if (!workspaceId.equals(membership.getWorkspaceId())) {
            throw new BadRequestException("Membership does not belong to workspace");
        }
        if (authorizationService.isSystemAdmin(membership.getUser().getId())) {
            throw new BadRequestException("Cannot change role for a system administrator");
        }
        if (RoleCodes.WORKSPACE_ADMIN.equals(membership.getRole().getCode())
                && !RoleCodes.WORKSPACE_ADMIN.equals(request.roleCode())
                && isPrimaryWorkspaceAdmin(membership)) {
            throw new BadRequestException("Primary workspace administrator role cannot be changed");
        }
        Role role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new BadRequestException("Unknown role: " + request.roleCode()));
        validateMemberRole(request.roleCode());

        String scope = request.scopeType() == null ? "WORKSPACE" : request.scopeType().toUpperCase();
        if (authorizationService.isTaskDerivedBoardRole(role.getCode())) {
            if (!"WORKSPACE".equals(scope)) {
                throw new BadRequestException(role.getCode() + " is assigned at workspace scope");
            }
        } else if (authorizationService.requiresManualBoardAssignment(role.getCode())) {
            if (!"BOARD".equals(scope) || request.boardId() == null) {
                throw new BadRequestException(role.getCode() + " must be assigned to a specific board");
            }
        }

        membership.setRole(role);
        if (authorizationService.isTaskDerivedBoardRole(role.getCode()) || "WORKSPACE".equals(scope)) {
            membership.setScopeType(ScopeType.WORKSPACE);
            membership.setBoardId(null);
        } else if ("BOARD".equals(scope)) {
            var board = boardRepository.findById(request.boardId())
                    .orElseThrow(() -> new NotFoundException("Board not found"));
            if (!board.getWorkspaceId().equals(workspaceId)) {
                throw new BadRequestException("Board does not belong to workspace");
            }
            membership.setScopeType(ScopeType.BOARD);
            membership.setBoardId(board.getId());
        } else {
            membership.setScopeType(ScopeType.WORKSPACE);
            membership.setBoardId(null);
        }
        MembershipDto saved = toMembershipDto(membershipRepository.save(membership));
        activityLogService.log(
                workspaceId,
                ActivityScopeLevel.WORKSPACE,
                null,
                null,
                actorId,
                ActivityAction.UPDATE,
                ActivityEntityType.MEMBERSHIP,
                saved.id(),
                "Updated member " + saved.userDisplayName() + " to role " + saved.roleCode(),
                null
        );
        return saved;
    }

    @Transactional
    public void syncMemberBoards(
            UUID actorId,
            UUID workspaceId,
            UUID userId,
            SyncMemberBoardsRequest request
    ) {
        requireCanManageWorkspaceMembers(actorId, workspaceId);
        validateMemberRole(request.roleCode());
        if (!authorizationService.requiresManualBoardAssignment(request.roleCode())) {
            throw new BadRequestException("Board assignment sync is only supported for board admin role");
        }
        if (authorizationService.isSystemAdmin(userId)) {
            throw new BadRequestException("Cannot update board access for a system administrator");
        }

        List<UUID> boardIds = request.boardIds() == null ? List.of() : request.boardIds();
        if (boardIds.isEmpty()) {
            throw new BadRequestException("At least one board is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Role role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new BadRequestException("Unknown role: " + request.roleCode()));

        List<Membership> existing = membershipRepository.findByWorkspaceId(workspaceId).stream()
                .filter(m -> userId.equals(m.getUser().getId()))
                .filter(m -> m.getScopeType() == ScopeType.BOARD)
                .filter(m -> request.roleCode().equals(m.getRole().getCode()))
                .toList();

        java.util.Set<UUID> desired = new java.util.HashSet<>(boardIds);
        java.util.Set<UUID> current = existing.stream()
                .map(Membership::getBoardId)
                .collect(java.util.stream.Collectors.toSet());

        for (Membership membership : existing) {
            if (!desired.contains(membership.getBoardId())) {
                membershipRepository.delete(membership);
            }
        }

        for (UUID boardId : desired) {
            if (!current.contains(boardId)) {
                saveBoardMembership(actorId, workspaceId, user, role, boardId);
            }
        }

        activityLogService.log(
                workspaceId,
                ActivityScopeLevel.WORKSPACE,
                null,
                null,
                actorId,
                ActivityAction.UPDATE,
                ActivityEntityType.MEMBERSHIP,
                userId,
                "Updated board access for " + user.getDisplayName() + " (" + request.roleCode() + ")",
                null
        );
    }

    @Transactional
    public void removeMembership(UUID actorId, UUID workspaceId, UUID membershipId) {
        requireCanManageWorkspaceMembers(actorId, workspaceId);
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new NotFoundException("Workspace not found");
        }
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NotFoundException("Membership not found"));
        if (!workspaceId.equals(membership.getWorkspaceId())) {
            throw new BadRequestException("Membership does not belong to workspace");
        }
        if (authorizationService.isSystemAdmin(membership.getUser().getId())) {
            throw new com.slatevn.web.ForbiddenException("Cannot remove system administrator membership");
        }
        if (isPrimaryWorkspaceAdmin(membership)) {
            throw new BadRequestException("Primary workspace administrator cannot be removed");
        }
        UUID memberUserId = membership.getUser().getId();
        boolean workspaceTaskDerived = membership.getScopeType() == ScopeType.WORKSPACE
                && authorizationService.isTaskDerivedBoardRole(membership.getRole().getCode());
        String removedRoleCode = membership.getRole().getCode();
        String memberName = membership.getUser().getDisplayName();
        membershipRepository.delete(membership);
        if (workspaceTaskDerived) {
            membershipRepository.findByWorkspaceId(workspaceId).stream()
                    .filter(m -> memberUserId.equals(m.getUser().getId()))
                    .filter(m -> m.getScopeType() == ScopeType.BOARD)
                    .filter(m -> removedRoleCode.equals(m.getRole().getCode()))
                    .forEach(membershipRepository::delete);
        }

        activityLogService.log(
                workspaceId,
                ActivityScopeLevel.WORKSPACE,
                null,
                null,
                actorId,
                ActivityAction.DELETE,
                ActivityEntityType.MEMBERSHIP,
                membershipId,
                "Removed member " + memberName,
                null
        );
    }

    private void requireCanManageWorkspaceMembers(UUID actorId, UUID workspaceId) {
        if (authorizationService.isSystemAdmin(actorId)) {
            throw new com.slatevn.web.ForbiddenException("System administrators cannot manage workspace members");
        }
        authorizationService.requireWorkspaceAdmin(actorId, workspaceId);
    }

    private Workspace requireActiveWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found"));
        if (workspace.isDeleted()) {
            throw new NotFoundException("Workspace not found");
        }
        return workspace;
    }

    private void requireCanViewWorkspace(UUID actorId, UUID workspaceId) {
        requireActiveWorkspace(workspaceId);
        if (!authorizationService.canViewWorkspace(actorId, workspaceId)) {
            throw new ForbiddenException("Workspace admin role required");
        }
    }

    private WorkspaceDto toDto(Workspace w) {
        return new WorkspaceDto(
                w.getId(),
                w.getName(),
                w.getKey(),
                w.getCreatedBy(),
                w.getCreatedAt(),
                w.getDeletedAt()
        );
    }

    private WorkspaceDetailDto toDetailDto(Workspace w, Set<String> permissions, boolean workspaceAdmin) {
        return new WorkspaceDetailDto(
                w.getId(),
                w.getName(),
                w.getKey(),
                w.getCreatedBy(),
                w.getCreatedAt(),
                List.copyOf(permissions),
                workspaceAdmin
        );
    }

    private MembershipDto toMembershipDto(Membership m) {
        return new MembershipDto(
                m.getId(),
                m.getUser().getId(),
                m.getUser().getEmail(),
                m.getUser().getDisplayName(),
                m.getUser().getAvatarUrl(),
                m.getRole().getCode(),
                m.getScopeType().name(),
                m.getWorkspaceId(),
                m.getBoardId(),
                authorizationService.isSystemAdmin(m.getUser().getId()),
                isPrimaryWorkspaceAdmin(m)
        );
    }

    private boolean isPrimaryWorkspaceAdmin(Membership membership) {
        if (!RoleCodes.WORKSPACE_ADMIN.equals(membership.getRole().getCode())) {
            return false;
        }
        if (membership.getScopeType() != ScopeType.WORKSPACE || membership.getWorkspaceId() == null) {
            return false;
        }
        return workspaceRepository.findById(membership.getWorkspaceId())
                .map(workspace -> membership.getUser().getId().equals(workspace.getOwnerId()))
                .orElse(false);
    }
}
