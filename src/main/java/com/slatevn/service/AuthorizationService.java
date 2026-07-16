package com.slatevn.service;

import com.slatevn.domain.Board;
import com.slatevn.domain.FieldDefinition;
import com.slatevn.domain.FieldVisibility;
import com.slatevn.domain.Membership;
import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.RoleCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.repository.BoardRepository;
import com.slatevn.repository.MembershipRepository;
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

    private final MembershipRepository membershipRepository;
    private final BoardRepository boardRepository;

    public AuthorizationService(MembershipRepository membershipRepository, BoardRepository boardRepository) {
        this.membershipRepository = membershipRepository;
        this.boardRepository = boardRepository;
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
        if (hasSystemPermission(userId, permissionCode)) {
            return true;
        }
        return membershipRepository.findByUserId(userId).stream()
                .filter(m -> appliesToWorkspace(m, workspaceId))
                .flatMap(m -> m.getRole().getPermissions().stream())
                .anyMatch(p -> p.getCode().equals(permissionCode));
    }

    @Transactional(readOnly = true)
    public boolean hasBoardPermission(UUID userId, UUID boardId, String permissionCode) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        if (hasSystemPermission(userId, permissionCode)) {
            return true;
        }
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
            if (m.getScopeType() == ScopeType.SYSTEM
                    || appliesToBoard(m, board.getWorkspaceId(), boardId)) {
                m.getRole().getPermissions().forEach(p -> codes.add(p.getCode()));
            }
        }
        return codes;
    }

    @Transactional(readOnly = true)
    public Set<String> resolveWorkspacePermissions(UUID userId, UUID workspaceId) {
        Set<String> codes = new HashSet<>();
        for (Membership m : membershipRepository.findByUserId(userId)) {
            if (m.getScopeType() == ScopeType.SYSTEM || appliesToWorkspace(m, workspaceId)) {
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
        if (!hasBoardPermission(userId, boardId, PermissionCodes.TASK_UPDATE)) {
            return false;
        }
        return field.isEditable();
    }

    @Transactional(readOnly = true)
    public List<UUID> visibleWorkspaceIds(UUID userId) {
        if (hasSystemPermission(userId, PermissionCodes.WORKSPACE_MANAGE)
                || hasSystemPermission(userId, PermissionCodes.USER_MANAGE)) {
            return null; // null means all
        }
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

    private boolean appliesToWorkspace(Membership m, UUID workspaceId) {
        if (RoleCodes.BOARD_VIEWER.equals(m.getRole().getCode())) {
            return false;
        }
        if (m.getScopeType() == ScopeType.SYSTEM) {
            return true;
        }
        if (m.getScopeType() == ScopeType.WORKSPACE) {
            return workspaceId.equals(m.getWorkspaceId());
        }
        if (m.getScopeType() == ScopeType.BOARD && m.getBoardId() != null) {
            return boardRepository.findById(m.getBoardId())
                    .map(b -> workspaceId.equals(b.getWorkspaceId()))
                    .orElse(false);
        }
        return false;
    }

    private boolean appliesToBoard(Membership m, UUID workspaceId, UUID boardId) {
        if (RoleCodes.BOARD_VIEWER.equals(m.getRole().getCode())) {
            return m.getScopeType() == ScopeType.BOARD && boardId.equals(m.getBoardId());
        }
        if (m.getScopeType() == ScopeType.SYSTEM) {
            return true;
        }
        if (m.getScopeType() == ScopeType.WORKSPACE) {
            return workspaceId.equals(m.getWorkspaceId());
        }
        if (m.getScopeType() == ScopeType.BOARD) {
            return boardId.equals(m.getBoardId());
        }
        return false;
    }
}
