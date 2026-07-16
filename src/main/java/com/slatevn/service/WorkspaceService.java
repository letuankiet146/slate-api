package com.slatevn.service;

import com.slatevn.domain.Membership;
import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.Role;
import com.slatevn.domain.RoleCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.User;
import com.slatevn.domain.Board;
import com.slatevn.domain.Workspace;
import com.slatevn.dto.AddMembershipRequest;
import com.slatevn.dto.CreateWorkspaceRequest;
import com.slatevn.dto.MembershipDto;
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

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            MembershipRepository membershipRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            BoardRepository boardRepository,
            BoardService boardService,
            AuthorizationService authorizationService,
            TaskTemplateService taskTemplateService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.boardRepository = boardRepository;
        this.boardService = boardService;
        this.authorizationService = authorizationService;
        this.taskTemplateService = taskTemplateService;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> list(UUID actorId) {
        List<UUID> visible = authorizationService.visibleWorkspaceIds(actorId);
        List<Workspace> all = workspaceRepository.findByDeletedAtIsNullOrderByNameAsc();
        if (visible == null) {
            return all.stream().map(this::toDto).toList();
        }
        return all.stream()
                .filter(w -> visible.contains(w.getId()))
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
        workspace.setName(request.name().trim());
        return toDto(workspaceRepository.save(workspace));
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
        if (!authorizationService.hasSystemPermission(actorId, PermissionCodes.WORKSPACE_MANAGE)
                && !authorizationService.hasSystemPermission(actorId, PermissionCodes.USER_MANAGE)) {
            throw new com.slatevn.web.ForbiddenException("Missing permission: WORKSPACE_MANAGE");
        }
        if (workspaceRepository.existsByKeyIgnoreCase(request.key())) {
            throw new BadRequestException("Workspace key already exists");
        }
        Workspace workspace = new Workspace();
        workspace.setName(request.name());
        workspace.setKey(request.key().toUpperCase());
        workspace.setCreatedBy(actorId);
        workspaceRepository.save(workspace);

        Role adminRole = roleRepository.findByCode("WORKSPACE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("WORKSPACE_ADMIN missing"));
        User actor = userRepository.findById(actorId).orElseThrow();
        Membership membership = new Membership();
        membership.setUser(actor);
        membership.setRole(adminRole);
        membership.setScopeType(ScopeType.WORKSPACE);
        membership.setWorkspaceId(workspace.getId());
        membershipRepository.save(membership);
        taskTemplateService.ensureDefaultTemplate(workspace.getId());

        return toDto(workspace);
    }

    @Transactional(readOnly = true)
    public List<MembershipDto> listMemberships(UUID actorId, UUID workspaceId) {
        authorizationService.requireWorkspacePermission(actorId, workspaceId, PermissionCodes.WORKSPACE_MANAGE);
        return membershipRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::toMembershipDto)
                .toList();
    }

    @Transactional
    public MembershipDto addMembership(UUID actorId, UUID workspaceId, AddMembershipRequest request) {
        authorizationService.requireWorkspacePermission(actorId, workspaceId, PermissionCodes.WORKSPACE_MANAGE);
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new NotFoundException("Workspace not found");
        }
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        Role role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new BadRequestException("Unknown role: " + request.roleCode()));

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

        return toMembershipDto(membershipRepository.save(membership));
    }

    @Transactional
    public void removeMembership(UUID actorId, UUID workspaceId, UUID membershipId) {
        authorizationService.requireWorkspacePermission(actorId, workspaceId, PermissionCodes.WORKSPACE_MANAGE);
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new NotFoundException("Workspace not found");
        }
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NotFoundException("Membership not found"));
        if (!workspaceId.equals(membership.getWorkspaceId())) {
            throw new BadRequestException("Membership does not belong to workspace");
        }
        membershipRepository.delete(membership);
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
        if (visible != null && !visible.contains(workspaceId)) {
            // also allow if they have any board permission via TASK_VIEW
            if (!authorizationService.hasWorkspacePermission(actorId, workspaceId, PermissionCodes.TASK_VIEW)
                    && !authorizationService.hasWorkspacePermission(actorId, workspaceId, PermissionCodes.WORKSPACE_MANAGE)
                    && !authorizationService.hasWorkspacePermission(actorId, workspaceId, PermissionCodes.BOARD_MANAGE)) {
                throw new com.slatevn.web.ForbiddenException("No access to workspace");
            }
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
                m.getRole().getCode(),
                m.getScopeType().name(),
                m.getWorkspaceId(),
                m.getBoardId()
        );
    }
}
