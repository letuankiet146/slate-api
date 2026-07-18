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
import com.slatevn.dto.CreateWorkspaceRequest;
import com.slatevn.dto.MembershipDto;
import com.slatevn.dto.UpdateMembershipRequest;
import com.slatevn.dto.UpdateWorkspaceRequest;
import com.slatevn.dto.WorkspaceDetailDto;
import com.slatevn.dto.WorkspaceDto;
import com.slatevn.repository.BoardRepository;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.RoleRepository;
import com.slatevn.repository.UserRepository;
import com.slatevn.repository.WorkspaceRepository;
import com.slatevn.web.BadRequestException;
import com.slatevn.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            MembershipRepository membershipRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            BoardRepository boardRepository,
            BoardService boardService,
            AuthorizationService authorizationService,
            TaskTemplateService taskTemplateService,
            ActivityLogService activityLogService
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
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> list(UUID actorId) {
        List<UUID> visible = authorizationService.visibleWorkspaceIds(actorId);
        List<Workspace> all = workspaceRepository.findByDeletedAtIsNullOrderByNameAsc();
        return all.stream()
                .filter(w -> visible.contains(w.getId()))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> listAllForAdmin(UUID actorId) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        return workspaceRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
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
        if (request.companyEmail() != null) {
            String companyEmail = request.companyEmail().trim();
            workspace.setCompanyEmail(companyEmail.isEmpty() ? null : companyEmail.toLowerCase());
        }
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
        if (request.companyEmail() != null) {
            activityLogService.log(
                    workspaceId,
                    ActivityScopeLevel.WORKSPACE,
                    null,
                    null,
                    actorId,
                    ActivityAction.UPDATE,
                    ActivityEntityType.WORKSPACE,
                    workspaceId,
                    "Updated company email for workspace \"" + workspace.getName() + "\"",
                    null
            );
        }

        return toDto(workspace);
    }

    @Transactional
    public void softDelete(UUID actorId, UUID workspaceId) {
        authorizationService.requireWorkspacePermission(actorId, workspaceId, PermissionCodes.WORKSPACE_MANAGE);
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
        authorizationService.requireWorkspaceAdmin(actorId, workspaceId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found"));
        if (!workspace.isDeleted()) {
            throw new BadRequestException("Workspace is not deleted");
        }
        workspace.setDeletedAt(null);
        workspace.setDeletedBy(null);
        workspaceRepository.save(workspace);

        boardRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .filter(Board::isDeleted)
                .forEach(board -> {
                    board.setDeletedAt(null);
                    board.setDeletedBy(null);
                    boardRepository.save(board);
                });

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
        authorizationService.requireAnyWorkspaceAdmin(actorId);
        return workspaceRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc().stream()
                .filter(w -> authorizationService.isWorkspaceAdmin(actorId, w.getId()))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasAnyWorkspaceAdminRole(UUID actorId) {
        return authorizationService.hasAnyWorkspaceAdminRole(actorId);
    }

    @Transactional
    public WorkspaceDto create(UUID actorId, CreateWorkspaceRequest request) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        if (workspaceRepository.existsByKeyIgnoreCase(request.key())) {
            throw new BadRequestException("Workspace key already exists");
        }
        Workspace workspace = new Workspace();
        workspace.setName(request.name());
        workspace.setKey(request.key().toUpperCase());
        workspace.setCreatedBy(actorId);
        workspaceRepository.save(workspace);
        taskTemplateService.ensureDefaultTemplate(workspace.getId());

        if (request.adminUserId() != null) {
            User adminUser = userRepository.findById(request.adminUserId())
                    .orElseThrow(() -> new NotFoundException("Workspace admin user not found"));
            if (authorizationService.isSystemAdmin(adminUser.getId())) {
                throw new BadRequestException("System administrators cannot be assigned as workspace admin");
            }
            Role adminRole = roleRepository.findByCode(RoleCodes.WORKSPACE_ADMIN)
                    .orElseThrow(() -> new IllegalStateException("WORKSPACE_ADMIN missing"));
            Membership membership = new Membership();
            membership.setUser(adminUser);
            membership.setRole(adminRole);
            membership.setScopeType(ScopeType.WORKSPACE);
            membership.setWorkspaceId(workspace.getId());
            membershipRepository.save(membership);
        }

        return toDto(workspace);
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
        if (!user.isEnabled()) {
            throw new BadRequestException("User is disabled");
        }
        if (authorizationService.isSystemAdmin(user.getId())) {
            throw new NotFoundException("User not found");
        }
        if (isAlreadyMember(user.getId(), workspaceId)) {
            throw new BadRequestException("User is already a member of this workspace");
        }
        return new AssignableUserDto(user.getId(), user.getEmail(), user.getDisplayName());
    }

    @Transactional
    public MembershipDto addMembership(UUID actorId, UUID workspaceId, AddMembershipRequest request) {
        requireCanManageWorkspaceMembers(actorId, workspaceId);
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new NotFoundException("Workspace not found");
        }
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (!user.isEnabled()) {
            throw new BadRequestException("User is disabled");
        }
        if (authorizationService.isSystemAdmin(user.getId())) {
            throw new BadRequestException("System administrators cannot be added as workspace members");
        }
        if (isAlreadyMember(user.getId(), workspaceId)) {
            throw new BadRequestException("User is already a member of this workspace");
        }
        Role role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new BadRequestException("Unknown role: " + request.roleCode()));
        if (RoleCodes.SYSTEM_ADMIN.equals(role.getCode())) {
            throw new BadRequestException("SYSTEM_ADMIN role cannot be assigned at workspace scope");
        }

        String scope = request.scopeType() == null ? "WORKSPACE" : request.scopeType().toUpperCase();
        if (RoleCodes.BOARD_VIEWER.equals(role.getCode())) {
            if (!"BOARD".equals(scope) || request.boardId() == null) {
                throw new BadRequestException("BOARD_VIEWER must be assigned to a specific board");
            }
        }

        Membership membership = new Membership();
        membership.setUser(user);
        membership.setRole(role);

        if ("BOARD".equals(scope)) {
            if (request.boardId() == null) {
                throw new BadRequestException("boardId required for BOARD scope");
            }
            var board = boardRepository.findById(request.boardId())
                    .orElseThrow(() -> new NotFoundException("Board not found"));
            if (!board.getWorkspaceId().equals(workspaceId)) {
                throw new BadRequestException("Board does not belong to workspace");
            }
            membership.setScopeType(ScopeType.BOARD);
            membership.setBoardId(board.getId());
            membership.setWorkspaceId(workspaceId);
        } else {
            membership.setScopeType(ScopeType.WORKSPACE);
            membership.setWorkspaceId(workspaceId);
        }

        MembershipDto saved = toMembershipDto(membershipRepository.save(membership));
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
        return saved;
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
        Role role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new BadRequestException("Unknown role: " + request.roleCode()));
        if (RoleCodes.SYSTEM_ADMIN.equals(role.getCode())) {
            throw new BadRequestException("SYSTEM_ADMIN role cannot be assigned at workspace scope");
        }

        String scope = request.scopeType() == null ? "WORKSPACE" : request.scopeType().toUpperCase();
        if (RoleCodes.BOARD_VIEWER.equals(role.getCode())) {
            if (!"BOARD".equals(scope) || request.boardId() == null) {
                throw new BadRequestException("BOARD_VIEWER must be assigned to a specific board");
            }
        }

        membership.setRole(role);
        if ("BOARD".equals(scope)) {
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
        String memberName = membership.getUser().getDisplayName();
        membershipRepository.delete(membership);

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

    private boolean isAlreadyMember(UUID userId, UUID workspaceId) {
        return membershipRepository.findByWorkspaceId(workspaceId).stream()
                .anyMatch(m -> userId.equals(m.getUser().getId()));
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
        List<UUID> visible = authorizationService.visibleWorkspaceIds(actorId);
        if (!visible.contains(workspaceId)) {
            throw new com.slatevn.web.ForbiddenException("No access to workspace");
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
                workspaceAdmin,
                w.getCompanyEmail()
        );
    }

    private MembershipDto toMembershipDto(Membership m) {
        return new MembershipDto(
                m.getId(),
                m.getUser().getId(),
                m.getUser().getEmail(),
                m.getUser().getDisplayName(),
                m.getRole().getCode(),
                m.getScopeType().name(),
                m.getWorkspaceId(),
                m.getBoardId(),
                authorizationService.isSystemAdmin(m.getUser().getId())
        );
    }
}
