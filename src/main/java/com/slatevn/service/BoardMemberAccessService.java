package com.slatevn.service;

import com.slatevn.domain.Board;
import com.slatevn.domain.Membership;
import com.slatevn.domain.Role;
import com.slatevn.domain.RoleCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.User;
import com.slatevn.repository.BoardRepository;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.RoleRepository;
import com.slatevn.repository.TaskRepository;
import com.slatevn.repository.UserRepository;
import com.slatevn.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BoardMemberAccessService {

    private final BoardRepository boardRepository;
    private final MembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    public BoardMemberAccessService(
            BoardRepository boardRepository,
            MembershipRepository membershipRepository,
            RoleRepository roleRepository,
            TaskRepository taskRepository,
            UserRepository userRepository,
            AuthorizationService authorizationService
    ) {
        this.boardRepository = boardRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public void onTaskAssigneeSet(UUID boardId, UUID assigneeId) {
        if (assigneeId == null) {
            return;
        }
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        UUID workspaceId = board.getWorkspaceId();

        if (authorizationService.isBoardAdminOnBoard(assigneeId, boardId)) {
            return;
        }

        String roleCode = resolveTaskAssignmentRole(assigneeId, workspaceId, boardId);
        if (roleCode == null) {
            return;
        }
        if (hasBoardScopedRole(assigneeId, workspaceId, boardId, roleCode)) {
            return;
        }

        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException(roleCode + " role missing"));
        User user = userRepository.findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Membership membership = new Membership();
        membership.setUser(user);
        membership.setRole(role);
        membership.setScopeType(ScopeType.BOARD);
        membership.setBoardId(boardId);
        membership.setWorkspaceId(workspaceId);
        membershipRepository.save(membership);
    }

    @Transactional
    public void onTaskAssigneeCleared(UUID boardId, UUID assigneeId) {
        if (assigneeId == null) {
            return;
        }
        maybeRemoveTaskDerivedBoardMembership(boardId, assigneeId);
    }

    /**
     * Role granted on a board when the user is assigned a task there.
     * Board admins on other boards receive BOARD_MEMBER on the task board.
     */
    private String resolveTaskAssignmentRole(UUID userId, UUID workspaceId, UUID boardId) {
        if (authorizationService.isWorkspaceBoardMember(userId, workspaceId)) {
            return RoleCodes.BOARD_MEMBER;
        }
        if (authorizationService.isWorkspaceBoardViewer(userId, workspaceId)) {
            return RoleCodes.BOARD_VIEWER;
        }
        if (authorizationService.hasBoardAdminInWorkspace(userId, workspaceId)) {
            return RoleCodes.BOARD_MEMBER;
        }
        return null;
    }

    private void maybeRemoveTaskDerivedBoardMembership(UUID boardId, UUID assigneeId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        UUID workspaceId = board.getWorkspaceId();

        if (authorizationService.isBoardAdminOnBoard(assigneeId, boardId)) {
            return;
        }

        long assignedTasks = taskRepository.countByBoardIdAndAssigneeIdAndDeletedAtIsNull(boardId, assigneeId);
        if (assignedTasks > 0) {
            return;
        }

        membershipRepository.findByWorkspaceId(workspaceId).stream()
                .filter(m -> assigneeId.equals(m.getUser().getId()))
                .filter(m -> m.getScopeType() == ScopeType.BOARD)
                .filter(m -> boardId.equals(m.getBoardId()))
                .filter(m -> authorizationService.isTaskDerivedBoardRole(m.getRole().getCode()))
                .forEach(membershipRepository::delete);
    }

    private boolean hasBoardScopedRole(
            UUID userId,
            UUID workspaceId,
            UUID boardId,
            String roleCode
    ) {
        return membershipRepository.findByWorkspaceId(workspaceId).stream()
                .anyMatch(m -> userId.equals(m.getUser().getId())
                        && m.getScopeType() == ScopeType.BOARD
                        && boardId.equals(m.getBoardId())
                        && roleCode.equals(m.getRole().getCode()));
    }
}
