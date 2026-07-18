package com.slatevn.service;

import com.slatevn.domain.ActivityLog;
import com.slatevn.domain.ActivityScopeLevel;
import com.slatevn.domain.Board;
import com.slatevn.domain.Membership;
import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.RoleCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.Task;
import com.slatevn.domain.User;
import com.slatevn.dto.ActivityLogDto;
import com.slatevn.repository.ActivityLogRepository;
import com.slatevn.repository.BoardRepository;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.TaskRepository;
import com.slatevn.repository.UserRepository;
import com.slatevn.repository.WorkspaceRepository;
import com.slatevn.web.ForbiddenException;
import com.slatevn.web.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ActivityLogService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MembershipRepository membershipRepository;
    private final AuthorizationService authorizationService;

    public ActivityLogService(
            ActivityLogRepository activityLogRepository,
            UserRepository userRepository,
            BoardRepository boardRepository,
            TaskRepository taskRepository,
            WorkspaceRepository workspaceRepository,
            MembershipRepository membershipRepository,
            AuthorizationService authorizationService
    ) {
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
        this.boardRepository = boardRepository;
        this.taskRepository = taskRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public void log(
            UUID workspaceId,
            ActivityScopeLevel scopeLevel,
            UUID boardId,
            UUID taskId,
            UUID actorId,
            String action,
            String entityType,
            UUID entityId,
            String summary,
            String details
    ) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("Actor not found: " + actorId));

        ActivityLog entry = new ActivityLog();
        entry.setWorkspaceId(workspaceId);
        entry.setScopeLevel(scopeLevel);
        entry.setBoardId(boardId);
        entry.setTaskId(taskId);
        entry.setActorId(actorId);
        entry.setActorName(actor.getDisplayName());
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setSummary(summary);
        entry.setDetails(details);
        activityLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<ActivityLogDto> listForWorkspace(UUID actorId, UUID workspaceId, Integer limit) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new NotFoundException("Workspace not found");
        }
        if (!authorizationService.visibleWorkspaceIds(actorId).contains(workspaceId)) {
            throw new ForbiddenException("No access to workspace");
        }

        Set<ActivityScopeLevel> scopeLevels = resolveHistoryScopeLevels(actorId, workspaceId);
        List<UUID> accessibleBoardIds = resolveAccessibleBoardIds(actorId, workspaceId);
        if (accessibleBoardIds.isEmpty() && !scopeLevels.contains(ActivityScopeLevel.WORKSPACE)) {
            return List.of();
        }
        if (accessibleBoardIds.isEmpty()) {
            accessibleBoardIds = List.of(UUID.randomUUID());
        }

        return activityLogRepository.findWorkspaceHistory(
                        workspaceId,
                        scopeLevels,
                        accessibleBoardIds,
                        PageRequest.of(0, clampLimit(limit))
                ).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityLogDto> listForBoard(UUID actorId, UUID boardId, Integer limit) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        requireBoardView(actorId, boardId);

        Set<ActivityScopeLevel> scopeLevels = resolveHistoryScopeLevels(actorId, board.getWorkspaceId());
        scopeLevels.remove(ActivityScopeLevel.WORKSPACE);
        if (scopeLevels.isEmpty()) {
            return List.of();
        }

        return activityLogRepository.findBoardHistory(boardId, scopeLevels, PageRequest.of(0, clampLimit(limit)))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityLogDto> listForTask(UUID actorId, UUID taskId, Integer limit) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        requireBoardView(actorId, task.getBoardId());

        Set<ActivityScopeLevel> scopeLevels = resolveHistoryScopeLevels(
                actorId,
                boardRepository.findById(task.getBoardId())
                        .map(Board::getWorkspaceId)
                        .orElseThrow(() -> new NotFoundException("Board not found"))
        );
        if (!scopeLevels.contains(ActivityScopeLevel.TASK)) {
            throw new ForbiddenException("No access to task history");
        }

        return activityLogRepository.findTaskHistory(taskId, PageRequest.of(0, clampLimit(limit)))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<ActivityScopeLevel> resolveHistoryScopeLevels(UUID userId, UUID workspaceId) {
        if (authorizationService.isWorkspaceAdmin(userId, workspaceId)) {
            return EnumSet.allOf(ActivityScopeLevel.class);
        }

        List<Membership> memberships = membershipRepository.findByUserId(userId).stream()
                .filter(m -> appliesToWorkspaceMembership(m, workspaceId))
                .toList();

        if (memberships.isEmpty()) {
            return Set.of();
        }

        boolean workspaceScopedMember = memberships.stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.WORKSPACE
                        && !RoleCodes.BOARD_VIEWER.equals(m.getRole().getCode()));
        if (workspaceScopedMember) {
            return EnumSet.of(ActivityScopeLevel.BOARD, ActivityScopeLevel.TASK);
        }

        boolean boardMember = memberships.stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.BOARD
                        && RoleCodes.BOARD_MEMBER.equals(m.getRole().getCode()));
        if (boardMember) {
            return EnumSet.of(ActivityScopeLevel.BOARD, ActivityScopeLevel.TASK);
        }

        return EnumSet.of(ActivityScopeLevel.TASK);
    }

    @Transactional(readOnly = true)
    public List<UUID> resolveAccessibleBoardIds(UUID userId, UUID workspaceId) {
        if (authorizationService.isWorkspaceAdmin(userId, workspaceId)) {
            return boardRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                    .map(Board::getId)
                    .toList();
        }

        List<Membership> memberships = membershipRepository.findByUserId(userId).stream()
                .filter(m -> appliesToWorkspaceMembership(m, workspaceId))
                .toList();

        boolean workspaceScoped = memberships.stream()
                .anyMatch(m -> m.getScopeType() == ScopeType.WORKSPACE
                        && !RoleCodes.BOARD_VIEWER.equals(m.getRole().getCode()));
        if (workspaceScoped) {
            return boardRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                    .map(Board::getId)
                    .toList();
        }

        List<UUID> boardIds = new ArrayList<>();
        for (Membership m : memberships) {
            if (m.getScopeType() == ScopeType.BOARD && m.getBoardId() != null) {
                boardIds.add(m.getBoardId());
            }
        }
        return boardIds;
    }

    private boolean appliesToWorkspaceMembership(Membership m, UUID workspaceId) {
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

    private void requireBoardView(UUID actorId, UUID boardId) {
        Set<String> permissions = authorizationService.resolveBoardPermissions(actorId, boardId);
        boolean canView = permissions.contains(PermissionCodes.TASK_VIEW)
                || permissions.contains(PermissionCodes.BOARD_MANAGE)
                || permissions.contains(PermissionCodes.TASK_VIEW_PUBLIC);
        if (!canView) {
            throw new ForbiddenException("No access to board");
        }
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private ActivityLogDto toDto(ActivityLog log) {
        return new ActivityLogDto(
                log.getId(),
                log.getWorkspaceId(),
                log.getScopeLevel().name(),
                log.getBoardId(),
                log.getTaskId(),
                log.getActorId(),
                log.getActorName(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getSummary(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}
