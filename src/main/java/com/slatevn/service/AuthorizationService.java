package com.slatevn.service;

import com.slatevn.domain.Board;
import com.slatevn.domain.FieldDefinition;
import com.slatevn.domain.FieldVisibility;
import com.slatevn.domain.Membership;
import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.RoleCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.Task;
import com.slatevn.repository.BoardRepository;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.TaskRepository;
import com.slatevn.web.ForbiddenException;
import com.slatevn.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthorizationService {

    private static final Set<String> TENANT_PERMISSIONS = Set.of(
            PermissionCodes.WORKSPACE_MANAGE,
            PermissionCodes.BOARD_MANAGE,
            PermissionCodes.TASK_CREATE,
            PermissionCodes.TASK_UPDATE,
            PermissionCodes.TASK_VIEW,
            PermissionCodes.TASK_VIEW_PUBLIC
    );

    private static final Set<String> BOARD_SCOPED_ROLES = Set.of(
            RoleCodes.BOARD_ADMIN,
            RoleCodes.BOARD_MEMBER,
            RoleCodes.BOARD_VIEWER
    );

    private static final Set<String> MANUAL_BOARD_ASSIGNMENT_ROLES = Set.of(
            RoleCodes.BOARD_ADMIN
    );

    private static final Set<String> TASK_DERIVED_BOARD_ROLES = Set.of(
            RoleCodes.BOARD_MEMBER,
            RoleCodes.BOARD_VIEWER
    );

    private final MembershipRepository membershipRepository;
    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;

    public AuthorizationService(
            MembershipRepository membershipRepository,
            BoardRepository boardRepository,
            TaskRepository taskRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.boardRepository = boardRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public boolean isSystemAdmin(UUID userId) {
        return membershipRepository.findByUserIdAndScopeType(userId, ScopeType.SYSTEM).stream()
                .anyMatch(m -> RoleCodes.SYSTEM_ADMIN.equals(m.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public boolean hasSystemPermission(UUID userId, String permissionCode) {
        return membershipRepository.findByUserIdAndScopeType(userId, ScopeType.SYSTEM).stream()
                .flatMap(m -> m.getRole().getPermissions().stream())
                .anyMatch(p -> p.getCode().equals(permissionCode));
    }

    @Transactional(readOnly = true)
    public boolean hasWorkspacePermission(UUID userId, UUID workspaceId, String permissionCode) {
        return membershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getScopeType() == ScopeType.WORKSPACE
                        && workspaceId.equals(m.getWorkspaceId()))
                .flatMap(m -> m.getRole().getPermissions().stream())
                .anyMatch(p -> p.getCode().equals(permissionCode));
    }

    @Transactional(readOnly = true)
    public boolean hasBoardPermission(UUID userId, UUID boardId, String permissionCode) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        return membershipRepository.findByUserId(userId).stream()
                .filter(m -> appliesToBoard(m, board.getWorkspaceId(), boardId))
                .flatMap(m -> m.getRole().getPermissions().stream())
                .anyMatch(p -> p.getCode().equals(permissionCode));
    }

    @Transactional(readOnly = true)
    public Set<String> resolveBoardPermissions(UUID userId, UUID boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        Set<String> codes = new HashSet<>();
        for (Membership m : membershipRepository.findByUserId(userId)) {
            if (appliesToBoard(m, board.getWorkspaceId(), boardId)) {
                m.getRole().getPermissions().forEach(p -> codes.add(p.getCode()));
            }
        }
        return codes;
    }

    @Transactional(readOnly = true)
    public Set<String> resolveWorkspacePermissions(UUID userId, UUID workspaceId) {
        Set<String> codes = new HashSet<>();
        for (Membership m : membershipRepository.findByUserId(userId)) {
            if (m.getScopeType() == ScopeType.WORKSPACE && workspaceId.equals(m.getWorkspaceId())) {
                m.getRole().getPermissions().forEach(p -> codes.add(p.getCode()));
            }
        }
        return codes;
    }

    @Transactional(readOnly = true)
    public boolean isWorkspaceAdmin(UUID userId, UUID workspaceId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.WORKSPACE
                        && workspaceId.equals(m.getWorkspaceId())
                        && RoleCodes.WORKSPACE_ADMIN.equals(m.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public boolean hasAnyWorkspaceAdminRole(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.WORKSPACE
                        && RoleCodes.WORKSPACE_ADMIN.equals(m.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public boolean hasAnyBoardAdminRole(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.BOARD
                        && RoleCodes.BOARD_ADMIN.equals(m.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public boolean isBoardAdminOnBoard(UUID userId, UUID boardId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.BOARD
                        && boardId.equals(m.getBoardId())
                        && RoleCodes.BOARD_ADMIN.equals(m.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public boolean hasBoardAdminInWorkspace(UUID userId, UUID workspaceId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.BOARD
                        && workspaceId.equals(m.getWorkspaceId())
                        && RoleCodes.BOARD_ADMIN.equals(m.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public boolean hasAssignedTasks(UUID userId) {
        return taskRepository.existsByAssigneeIdAndDeletedAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public boolean canAccessMyTasks(UUID userId) {
        return hasAssignedTasks(userId)
                || isBoardScopedMemberOrViewer(userId)
                || hasAnyBoardAdminRole(userId);
    }

    @Transactional(readOnly = true)
    public boolean canViewWorkspace(UUID userId, UUID workspaceId) {
        if (isSystemAdmin(userId)) {
            return true;
        }
        return isWorkspaceAdmin(userId, workspaceId);
    }

    @Transactional(readOnly = true)
    public boolean canAccessManagedBoards(UUID userId) {
        return hasAnyBoardAdminRole(userId);
    }

    @Transactional(readOnly = true)
    public boolean canAccessWorkspaces(UUID userId) {
        if (isSystemAdmin(userId)) {
            return true;
        }
        return !workspaceAdminWorkspaceIds(userId).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean isBoardScopedMemberOrViewer(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> (m.getScopeType() == ScopeType.WORKSPACE
                        && TASK_DERIVED_BOARD_ROLES.contains(m.getRole().getCode()))
                        || (m.getScopeType() == ScopeType.BOARD
                        && TASK_DERIVED_BOARD_ROLES.contains(m.getRole().getCode())));
    }

    @Transactional(readOnly = true)
    public boolean isWorkspaceBoardMember(UUID userId, UUID workspaceId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.WORKSPACE
                        && workspaceId.equals(m.getWorkspaceId())
                        && RoleCodes.BOARD_MEMBER.equals(m.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public boolean isWorkspaceBoardViewer(UUID userId, UUID workspaceId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.WORKSPACE
                        && workspaceId.equals(m.getWorkspaceId())
                        && RoleCodes.BOARD_VIEWER.equals(m.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public boolean hasWorkspaceOrBoardMemberRole(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> RoleCodes.BOARD_MEMBER.equals(m.getRole().getCode())
                        && (m.getScopeType() == ScopeType.WORKSPACE || m.getScopeType() == ScopeType.BOARD));
    }

    @Transactional(readOnly = true)
    public boolean hasWorkspaceOrBoardViewerRole(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> RoleCodes.BOARD_VIEWER.equals(m.getRole().getCode())
                        && (m.getScopeType() == ScopeType.WORKSPACE || m.getScopeType() == ScopeType.BOARD));
    }

    @Transactional(readOnly = true)
    public boolean isBoardViewerOnBoard(UUID userId, UUID boardId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.BOARD
                        && boardId.equals(m.getBoardId())
                        && RoleCodes.BOARD_VIEWER.equals(m.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public boolean canManageBoard(UUID userId, UUID boardId) {
        return hasBoardPermission(userId, boardId, PermissionCodes.BOARD_MANAGE);
    }

    @Transactional(readOnly = true)
    public boolean canCreateTask(UUID userId, UUID boardId) {
        return hasBoardPermission(userId, boardId, PermissionCodes.TASK_CREATE);
    }

    @Transactional(readOnly = true)
    public boolean canViewTask(UUID userId, Task task) {
        UUID boardId = task.getBoardId();
        if (canManageBoard(userId, boardId)) {
            return true;
        }
        if (hasBoardPermission(userId, boardId, PermissionCodes.TASK_VIEW)) {
            if (isAssigneeOnlyRole(userId, boardId)) {
                return userId.equals(task.getAssigneeId());
            }
            return true;
        }
        if (hasBoardPermission(userId, boardId, PermissionCodes.TASK_VIEW_PUBLIC)) {
            return userId.equals(task.getAssigneeId());
        }
        return false;
    }

    @Transactional(readOnly = true)
    public boolean canUpdateTask(UUID userId, Task task) {
        UUID boardId = task.getBoardId();
        if (canManageBoard(userId, boardId)) {
            return true;
        }
        if (isBoardViewerOnBoard(userId, boardId)) {
            return false;
        }
        if (hasBoardPermission(userId, boardId, PermissionCodes.TASK_UPDATE)) {
            return userId.equals(task.getAssigneeId());
        }
        return false;
    }

    @Transactional(readOnly = true)
    public boolean canCommentOnTask(UUID userId, Task task) {
        if (!canViewTask(userId, task)) {
            return false;
        }
        return !isBoardViewerOnBoard(userId, task.getBoardId());
    }

    public void requireCanViewTask(UUID userId, Task task) {
        if (!canViewTask(userId, task)) {
            throw new ForbiddenException("No access to task");
        }
    }

    public void requireCanUpdateTask(UUID userId, Task task) {
        if (!canUpdateTask(userId, task)) {
            throw new ForbiddenException("Cannot update task");
        }
    }

    @Transactional(readOnly = true)
    public boolean isAssigneeOnlyOnBoard(UUID userId, UUID boardId) {
        return isAssigneeOnlyRole(userId, boardId);
    }

    @Transactional(readOnly = true)
    public boolean canDeleteTask(UUID userId, Task task) {
        return canManageBoard(userId, task.getBoardId());
    }

    @Transactional(readOnly = true)
    public boolean canChangeTaskAssignee(UUID userId, Task task) {
        return canManageBoard(userId, task.getBoardId());
    }

    public void requireCanDeleteTask(UUID userId, Task task) {
        if (!canDeleteTask(userId, task)) {
            throw new ForbiddenException("Cannot delete task");
        }
    }

    public void requireCanCreateTask(UUID userId, UUID boardId) {
        if (!canCreateTask(userId, boardId)) {
            throw new ForbiddenException("Cannot create tasks on this board");
        }
    }

    public void requireWorkspaceAdmin(UUID userId, UUID workspaceId) {
        if (!isWorkspaceAdmin(userId, workspaceId)) {
            throw new ForbiddenException("Workspace admin role required");
        }
    }

    public void requireAnyWorkspaceAdmin(UUID userId) {
        if (!hasAnyWorkspaceAdminRole(userId)) {
            throw new ForbiddenException("Workspace admin role required");
        }
    }

    public void requireSystemPermission(UUID userId, String permissionCode) {
        if (!hasSystemPermission(userId, permissionCode)) {
            throw new ForbiddenException("Missing permission: " + permissionCode);
        }
    }

    public void requireWorkspacePermission(UUID userId, UUID workspaceId, String permissionCode) {
        if (!hasWorkspacePermission(userId, workspaceId, permissionCode)) {
            throw new ForbiddenException("Missing permission: " + permissionCode);
        }
    }

    public void requireBoardPermission(UUID userId, UUID boardId, String permissionCode) {
        if (!hasBoardPermission(userId, boardId, permissionCode)) {
            throw new ForbiddenException("Missing permission: " + permissionCode);
        }
    }

    @Transactional(readOnly = true)
    public boolean canViewInternal(UUID userId, UUID boardId) {
        if (isBoardViewerOnBoard(userId, boardId)) {
            return false;
        }
        return hasBoardPermission(userId, boardId, PermissionCodes.TASK_VIEW)
                || hasBoardPermission(userId, boardId, PermissionCodes.BOARD_MANAGE);
    }

    @Transactional(readOnly = true)
    public boolean canViewField(UUID userId, UUID boardId, FieldDefinition field) {
        if (field.getVisibility() == FieldVisibility.PUBLIC) {
            return hasBoardPermission(userId, boardId, PermissionCodes.TASK_VIEW)
                    || hasBoardPermission(userId, boardId, PermissionCodes.TASK_VIEW_PUBLIC)
                    || hasBoardPermission(userId, boardId, PermissionCodes.BOARD_MANAGE);
        }
        return canViewInternal(userId, boardId);
    }

    @Transactional(readOnly = true)
    public boolean canEditField(UUID userId, UUID boardId, FieldDefinition field) {
        if (hasBoardPermission(userId, boardId, PermissionCodes.BOARD_MANAGE)) {
            return true;
        }
        if (isBoardViewerOnBoard(userId, boardId)) {
            return false;
        }
        if (!hasBoardPermission(userId, boardId, PermissionCodes.TASK_UPDATE)) {
            return false;
        }
        return field.isEditable();
    }

    @Transactional(readOnly = true)
    public List<UUID> visibleWorkspaceIds(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getWorkspaceId() != null || m.getBoardId() != null)
                .map(m -> {
                    if (m.getWorkspaceId() != null) {
                        return m.getWorkspaceId();
                    }
                    return boardRepository.findById(m.getBoardId())
                            .map(Board::getWorkspaceId)
                            .orElse(null);
                })
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> visibleBoardIds(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .flatMap(m -> resolveBoardIdsForMembership(m).stream())
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> workspaceAdminWorkspaceIds(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getScopeType() == ScopeType.WORKSPACE
                        && RoleCodes.WORKSPACE_ADMIN.equals(m.getRole().getCode())
                        && m.getWorkspaceId() != null)
                .map(Membership::getWorkspaceId)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasAnyTenantMembership(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getScopeType() != ScopeType.SYSTEM);
    }

    @Transactional(readOnly = true)
    public boolean isTenantPermission(String permissionCode) {
        return TENANT_PERMISSIONS.contains(permissionCode);
    }

    @Transactional(readOnly = true)
    public boolean isBoardScopedRole(String roleCode) {
        return BOARD_SCOPED_ROLES.contains(roleCode);
    }

    @Transactional(readOnly = true)
    public boolean requiresManualBoardAssignment(String roleCode) {
        return MANUAL_BOARD_ASSIGNMENT_ROLES.contains(roleCode);
    }

    @Transactional(readOnly = true)
    public boolean isTaskDerivedBoardRole(String roleCode) {
        return TASK_DERIVED_BOARD_ROLES.contains(roleCode);
    }

    private boolean isAssigneeOnlyRole(UUID userId, UUID boardId) {
        if (canManageBoard(userId, boardId)) {
            return false;
        }
        return membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.BOARD
                        && boardId.equals(m.getBoardId())
                        && (RoleCodes.BOARD_MEMBER.equals(m.getRole().getCode())
                        || RoleCodes.BOARD_VIEWER.equals(m.getRole().getCode())));
    }

    private List<UUID> resolveBoardIdsForMembership(Membership m) {
        if (m.getScopeType() == ScopeType.BOARD && m.getBoardId() != null) {
            return List.of(m.getBoardId());
        }
        if (m.getScopeType() == ScopeType.WORKSPACE && m.getWorkspaceId() != null
                && RoleCodes.WORKSPACE_ADMIN.equals(m.getRole().getCode())) {
            return boardRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(m.getWorkspaceId())
                    .stream()
                    .map(Board::getId)
                    .toList();
        }
        return List.of();
    }

    private boolean appliesToBoard(Membership m, UUID workspaceId, UUID boardId) {
        if (m.getScopeType() == ScopeType.WORKSPACE) {
            return workspaceId.equals(m.getWorkspaceId())
                    && RoleCodes.WORKSPACE_ADMIN.equals(m.getRole().getCode());
        }
        if (m.getScopeType() == ScopeType.BOARD) {
            return boardId.equals(m.getBoardId());
        }
        return false;
    }
}
