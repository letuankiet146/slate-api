package com.slatevn.service;

import com.slatevn.domain.ActivityAction;
import com.slatevn.domain.ActivityEntityType;
import com.slatevn.domain.ActivityScopeLevel;
import com.slatevn.domain.Board;
import com.slatevn.domain.BoardColumn;
import com.slatevn.domain.FieldDefinition;
import com.slatevn.domain.Membership;
import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.Task;
import com.slatevn.domain.TaskFieldValue;
import com.slatevn.domain.TaskTemplate;
import com.slatevn.domain.Workspace;
import com.slatevn.dto.BoardDto;
import com.slatevn.dto.BoardMemberDto;
import com.slatevn.dto.BoardViewDto;
import com.slatevn.dto.ColumnDto;
import com.slatevn.dto.CreateBoardRequest;
import com.slatevn.dto.CreateColumnRequest;
import com.slatevn.dto.FieldDefinitionDto;
import com.slatevn.dto.TaskDto;
import com.slatevn.dto.TaskFieldValueDto;
import com.slatevn.dto.TaskTemplateDto;
import com.slatevn.dto.UpdateBoardRequest;
import com.slatevn.repository.BoardColumnRepository;
import com.slatevn.repository.BoardRepository;
import com.slatevn.repository.FieldDefinitionRepository;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.TaskFieldValueRepository;
import com.slatevn.repository.TaskRepository;
import com.slatevn.repository.TaskTemplateRepository;
import com.slatevn.repository.WorkspaceRepository;
import com.slatevn.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BoardService {

    public static final String DEFAULT_TODO_COLUMN_NAME = "Cần làm";

    private final BoardRepository boardRepository;
    private final WorkspaceRepository workspaceRepository;
    private final BoardColumnRepository columnRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final TaskRepository taskRepository;
    private final TaskFieldValueRepository taskFieldValueRepository;
    private final TaskTemplateRepository templateRepository;
    private final MembershipRepository membershipRepository;
    private final AuthorizationService authorizationService;
    private final TaskTemplateService taskTemplateService;
    private final ActivityLogService activityLogService;

    public BoardService(
            BoardRepository boardRepository,
            WorkspaceRepository workspaceRepository,
            BoardColumnRepository columnRepository,
            FieldDefinitionRepository fieldDefinitionRepository,
            TaskRepository taskRepository,
            TaskFieldValueRepository taskFieldValueRepository,
            TaskTemplateRepository templateRepository,
            MembershipRepository membershipRepository,
            AuthorizationService authorizationService,
            TaskTemplateService taskTemplateService,
            ActivityLogService activityLogService
    ) {
        this.boardRepository = boardRepository;
        this.workspaceRepository = workspaceRepository;
        this.columnRepository = columnRepository;
        this.fieldDefinitionRepository = fieldDefinitionRepository;
        this.taskRepository = taskRepository;
        this.taskFieldValueRepository = taskFieldValueRepository;
        this.templateRepository = templateRepository;
        this.membershipRepository = membershipRepository;
        this.authorizationService = authorizationService;
        this.taskTemplateService = taskTemplateService;
        this.activityLogService = activityLogService;
    }

    @Transactional(readOnly = true)
    public List<BoardDto> listByWorkspace(UUID actorId, UUID workspaceId) {
        requireActiveWorkspace(workspaceId);
        return boardRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(workspaceId).stream()
                .filter(b -> authorizationService.hasBoardPermission(actorId, b.getId(), PermissionCodes.TASK_VIEW)
                        || authorizationService.hasBoardPermission(actorId, b.getId(), PermissionCodes.BOARD_MANAGE)
                        || authorizationService.hasBoardPermission(actorId, b.getId(), PermissionCodes.TASK_VIEW_PUBLIC))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public BoardDto create(UUID actorId, UUID workspaceId, CreateBoardRequest request) {
        requireActiveWorkspace(workspaceId);
        authorizationService.requireWorkspacePermission(actorId, workspaceId, PermissionCodes.BOARD_MANAGE);

        Board board = new Board();
        board.setWorkspaceId(workspaceId);
        board.setName(request.name());
        board.setCreatedBy(actorId);
        boardRepository.save(board);

        createDefaultColumns(board.getId());
        taskTemplateService.linkBoardToDefaultTemplate(workspaceId, board.getId());

        activityLogService.log(
                workspaceId,
                ActivityScopeLevel.BOARD,
                board.getId(),
                null,
                actorId,
                ActivityAction.CREATE,
                ActivityEntityType.BOARD,
                board.getId(),
                "Created board \"" + board.getName() + "\"",
                null
        );

        return toDto(board);
    }

    @Transactional
    public BoardDto update(UUID actorId, UUID boardId, UpdateBoardRequest request) {
        authorizationService.requireBoardPermission(actorId, boardId, PermissionCodes.BOARD_MANAGE);
        Board board = requireActiveBoard(boardId);
        String oldName = board.getName();
        board.setName(request.name().trim());
        boardRepository.save(board);

        if (!oldName.equals(board.getName())) {
            activityLogService.log(
                    board.getWorkspaceId(),
                    ActivityScopeLevel.BOARD,
                    board.getId(),
                    null,
                    actorId,
                    ActivityAction.UPDATE,
                    ActivityEntityType.BOARD,
                    board.getId(),
                    "Renamed board \"" + oldName + "\" to \"" + board.getName() + "\"",
                    null
            );
        }

        return toDto(board);
    }

    @Transactional(readOnly = true)
    public BoardViewDto getView(UUID actorId, UUID boardId) {
        Board board = requireActiveBoard(boardId);

        Set<String> permissions = authorizationService.resolveBoardPermissions(actorId, boardId);
        boolean canView = permissions.contains(PermissionCodes.TASK_VIEW)
                || permissions.contains(PermissionCodes.BOARD_MANAGE)
                || permissions.contains(PermissionCodes.TASK_VIEW_PUBLIC);
        if (!canView) {
            throw new com.slatevn.web.ForbiddenException("No access to board");
        }

        List<ColumnDto> columns = columnRepository.findByBoardIdOrderByPositionAsc(boardId).stream()
                .map(this::toColumnDto)
                .toList();

        List<TaskTemplate> visibleTemplates = templateRepository.findVisibleOnBoard(boardId);
        List<TaskTemplateDto> templateDtos = visibleTemplates.stream()
                .map(taskTemplateService::toDto)
                .toList();

        List<Task> tasks = taskRepository.findByBoardIdAndDeletedAtIsNullOrderByPositionAsc(boardId);
        List<UUID> taskIds = tasks.stream().map(Task::getId).toList();
        Map<UUID, List<TaskFieldValue>> valuesByTask = taskIds.isEmpty()
                ? Map.of()
                : taskFieldValueRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.groupingBy(TaskFieldValue::getTaskId));

        Map<UUID, List<FieldDefinition>> taskSpecificByTask = fieldDefinitionRepository
                .findByBoardIdAndTaskIdIsNotNull(boardId).stream()
                .collect(Collectors.groupingBy(FieldDefinition::getTaskId));

        Set<UUID> templateIds = new java.util.HashSet<>(
                visibleTemplates.stream().map(TaskTemplate::getId).toList()
        );
        tasks.stream()
                .map(Task::getTemplateId)
                .filter(java.util.Objects::nonNull)
                .forEach(templateIds::add);

        Map<UUID, TaskTemplate> templateMap = new java.util.HashMap<>();
        Map<UUID, List<FieldDefinition>> fieldsByTemplate = new java.util.HashMap<>();
        for (UUID templateId : templateIds) {
            templateRepository.findById(templateId).ifPresent(t -> templateMap.put(t.getId(), t));
            fieldsByTemplate.put(
                    templateId,
                    fieldDefinitionRepository.findByTemplateIdOrderByPositionAsc(templateId)
            );
        }

        List<TaskDto> taskDtos = new ArrayList<>();
        for (Task task : tasks) {
            Map<UUID, String> values = valuesByTask.getOrDefault(task.getId(), List.of()).stream()
                    .collect(Collectors.toMap(
                            TaskFieldValue::getFieldDefinitionId,
                            TaskFieldValue::getValue,
                            (a, b) -> a
                    ));

            List<FieldDefinition> applicable = new ArrayList<>(
                    fieldsByTemplate.getOrDefault(task.getTemplateId(), List.of())
            );
            applicable.addAll(taskSpecificByTask.getOrDefault(task.getId(), List.of()));
            applicable.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));

            List<TaskFieldValueDto> fieldDtos = new ArrayList<>();
            for (FieldDefinition def : applicable) {
                if (!authorizationService.canViewField(actorId, boardId, def)) {
                    continue;
                }
                fieldDtos.add(toTaskFieldDto(def, values.get(def.getId())));
            }

            TaskTemplate template = task.getTemplateId() != null ? templateMap.get(task.getTemplateId()) : null;
            taskDtos.add(new TaskDto(
                    task.getId(), task.getBoardId(), task.getColumnId(), task.getTitle(),
                    task.getDescription(), task.getCreatedBy(), task.getCreatedByName(), task.getAssigneeId(),
                    task.getTemplateId(),
                    template != null ? template.getName() : null,
                    task.getPosition(), fieldDtos, task.getCreatedAt(), task.getUpdatedAt(),
                    task.getDeletedAt()
            ));
        }

        return new BoardViewDto(toDto(board), columns, templateDtos, taskDtos, permissions);
    }

    @Transactional(readOnly = true)
    public List<BoardMemberDto> listMembers(UUID actorId, UUID boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        Set<String> permissions = authorizationService.resolveBoardPermissions(actorId, boardId);
        boolean canView = permissions.contains(PermissionCodes.TASK_VIEW)
                || permissions.contains(PermissionCodes.BOARD_MANAGE)
                || permissions.contains(PermissionCodes.TASK_VIEW_PUBLIC);
        if (!canView) {
            throw new com.slatevn.web.ForbiddenException("No access to board");
        }

        List<Membership> memberships = membershipRepository.findByWorkspaceId(board.getWorkspaceId());
        Map<UUID, BoardMemberDto> members = new LinkedHashMap<>();
        for (Membership m : memberships) {
            if (m.getScopeType() == ScopeType.WORKSPACE
                    || (m.getScopeType() == ScopeType.BOARD && boardId.equals(m.getBoardId()))) {
                members.putIfAbsent(
                        m.getUser().getId(),
                        new BoardMemberDto(
                                m.getUser().getId(),
                                m.getUser().getEmail(),
                                m.getUser().getDisplayName(),
                                m.getUser().getAvatarUrl()
                        )
                );
            }
        }
        return new ArrayList<>(members.values());
    }

    @Transactional
    public ColumnDto createColumn(UUID actorId, UUID boardId, CreateColumnRequest request) {
        authorizationService.requireBoardPermission(actorId, boardId, PermissionCodes.BOARD_MANAGE);
        ensureBoard(boardId);
        int position = request.position() != null
                ? request.position()
                : columnRepository.findByBoardIdOrderByPositionAsc(boardId).size();
        BoardColumn column = new BoardColumn();
        column.setBoardId(boardId);
        column.setName(request.name().trim());
        column.setPosition(position);
        BoardColumn saved = columnRepository.save(column);

        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        activityLogService.log(
                board.getWorkspaceId(),
                ActivityScopeLevel.BOARD,
                boardId,
                null,
                actorId,
                ActivityAction.CREATE,
                ActivityEntityType.COLUMN,
                saved.getId(),
                "Added column \"" + saved.getName() + "\" to board \"" + board.getName() + "\"",
                null
        );

        return toColumnDto(saved);
    }

    @Transactional
    public void deleteColumn(UUID actorId, UUID boardId, UUID columnId) {
        authorizationService.requireBoardPermission(actorId, boardId, PermissionCodes.BOARD_MANAGE);
        ensureBoard(boardId);

        BoardColumn column = columnRepository.findByIdAndBoardId(columnId, boardId)
                .orElseThrow(() -> new NotFoundException("Column not found"));
        String columnName = column.getName();
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));

        List<BoardColumn> columns = columnRepository.findByBoardIdOrderByPositionAsc(boardId);
        if (columns.size() <= 1) {
            throw new com.slatevn.web.BadRequestException("Cannot delete the last status column");
        }

        Instant now = Instant.now();
        List<Task> tasksInColumn = taskRepository.findByColumnIdAndDeletedAtIsNullOrderByPositionAsc(columnId);
        for (Task task : tasksInColumn) {
            task.setDeletedAt(now);
            task.setDeletedBy(actorId);
            task.setColumnId(null);
            taskRepository.save(task);
        }

        columnRepository.delete(column);

        activityLogService.log(
                board.getWorkspaceId(),
                ActivityScopeLevel.BOARD,
                boardId,
                null,
                actorId,
                ActivityAction.DELETE,
                ActivityEntityType.COLUMN,
                columnId,
                "Deleted column \"" + columnName + "\" from board \"" + board.getName() + "\"",
                null
        );

        List<BoardColumn> remaining = columnRepository.findByBoardIdOrderByPositionAsc(boardId);
        for (int i = 0; i < remaining.size(); i++) {
            BoardColumn remainingColumn = remaining.get(i);
            remainingColumn.setPosition(i);
            columnRepository.save(remainingColumn);
        }
    }

    @Transactional
    public List<ColumnDto> reorderColumns(UUID actorId, UUID boardId, List<UUID> columnIds) {
        authorizationService.requireBoardPermission(actorId, boardId, PermissionCodes.BOARD_MANAGE);
        ensureBoard(boardId);

        List<BoardColumn> existing = columnRepository.findByBoardIdOrderByPositionAsc(boardId);
        if (columnIds == null || columnIds.isEmpty()) {
            throw new com.slatevn.web.BadRequestException("columnIds required");
        }
        if (columnIds.size() != existing.size()) {
            throw new com.slatevn.web.BadRequestException("columnIds must include every column on the board");
        }

        Map<UUID, BoardColumn> byId = existing.stream()
                .collect(Collectors.toMap(BoardColumn::getId, c -> c));
        for (UUID columnId : columnIds) {
            if (!byId.containsKey(columnId)) {
                throw new com.slatevn.web.BadRequestException("Invalid column for board: " + columnId);
            }
        }
        if (columnIds.stream().distinct().count() != columnIds.size()) {
            throw new com.slatevn.web.BadRequestException("Duplicate columnIds");
        }

        for (int i = 0; i < columnIds.size(); i++) {
            BoardColumn column = byId.get(columnIds.get(i));
            column.setPosition(i);
            columnRepository.save(column);
        }

        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        activityLogService.log(
                board.getWorkspaceId(),
                ActivityScopeLevel.BOARD,
                boardId,
                null,
                actorId,
                ActivityAction.UPDATE,
                ActivityEntityType.COLUMN,
                null,
                "Reordered columns on board \"" + board.getName() + "\"",
                null
        );

        return columnRepository.findByBoardIdOrderByPositionAsc(boardId).stream()
                .map(this::toColumnDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ColumnDto> listColumns(UUID actorId, UUID boardId) {
        authorizationService.requireBoardPermission(actorId, boardId, PermissionCodes.TASK_VIEW);
        return columnRepository.findByBoardIdOrderByPositionAsc(boardId).stream()
                .map(this::toColumnDto)
                .toList();
    }

    @Transactional
    public void softDelete(UUID actorId, UUID boardId) {
        authorizationService.requireBoardPermission(actorId, boardId, PermissionCodes.BOARD_MANAGE);
        Board board = requireActiveBoard(boardId);
        softDeleteBoard(actorId, board, Instant.now());

        activityLogService.log(
                board.getWorkspaceId(),
                ActivityScopeLevel.BOARD,
                board.getId(),
                null,
                actorId,
                ActivityAction.DELETE,
                ActivityEntityType.BOARD,
                board.getId(),
                "Deleted board \"" + board.getName() + "\"",
                null
        );
    }

    @Transactional
    public void softDeleteBoardsInWorkspace(UUID actorId, UUID workspaceId, Instant deletedAt) {
        List<Board> boards = boardRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(workspaceId);
        for (Board board : boards) {
            softDeleteBoard(actorId, board, deletedAt);
        }
    }

    @Transactional
    public void restoreBoardsInWorkspace(UUID workspaceId) {
        boardRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .filter(Board::isDeleted)
                .forEach(board -> {
                    board.setDeletedAt(null);
                    board.setDeletedBy(null);
                    boardRepository.save(board);
                });
    }

    @Transactional
    public BoardDto restore(UUID actorId, UUID boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        if (!board.isDeleted()) {
            throw new com.slatevn.web.BadRequestException("Board is not deleted");
        }
        authorizationService.requireWorkspaceAdmin(actorId, board.getWorkspaceId());
        requireActiveWorkspace(board.getWorkspaceId());

        board.setDeletedAt(null);
        board.setDeletedBy(null);
        boardRepository.save(board);

        activityLogService.log(
                board.getWorkspaceId(),
                ActivityScopeLevel.BOARD,
                board.getId(),
                null,
                actorId,
                ActivityAction.RESTORE,
                ActivityEntityType.BOARD,
                board.getId(),
                "Restored board \"" + board.getName() + "\"",
                null
        );

        return toDto(board);
    }

    @Transactional(readOnly = true)
    public List<BoardDto> listDeletedInWorkspace(UUID actorId, UUID workspaceId) {
        authorizationService.requireWorkspaceAdmin(actorId, workspaceId);
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new NotFoundException("Workspace not found");
        }
        return boardRepository.findByWorkspaceIdInAndDeletedAtIsNotNullOrderByDeletedAtDesc(List.of(workspaceId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private void softDeleteBoard(UUID actorId, Board board, Instant deletedAt) {
        List<Task> tasks = taskRepository.findByBoardIdAndDeletedAtIsNullOrderByPositionAsc(board.getId());
        for (Task task : tasks) {
            task.setDeletedAt(deletedAt);
            task.setDeletedBy(actorId);
            task.setColumnId(null);
            taskRepository.save(task);
        }
        board.setDeletedAt(deletedAt);
        board.setDeletedBy(actorId);
        boardRepository.save(board);
    }

    private void createDefaultColumns(UUID boardId) {
        String[] names = {DEFAULT_TODO_COLUMN_NAME, "Đang làm", "Hoàn thành"};
        for (int i = 0; i < names.length; i++) {
            BoardColumn column = new BoardColumn();
            column.setBoardId(boardId);
            column.setName(names[i]);
            column.setPosition(i);
            columnRepository.save(column);
        }
    }

    private void ensureBoard(UUID boardId) {
        requireActiveBoard(boardId);
    }

    private Board requireActiveBoard(UUID boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        if (board.isDeleted()) {
            throw new NotFoundException("Board not found");
        }
        return board;
    }

    private void requireActiveWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found"));
        if (workspace.isDeleted()) {
            throw new NotFoundException("Workspace not found");
        }
    }

    private BoardDto toDto(Board b) {
        return new BoardDto(
                b.getId(),
                b.getWorkspaceId(),
                b.getName(),
                b.getCreatedBy(),
                b.getCreatedAt(),
                b.getDeletedAt()
        );
    }

    private ColumnDto toColumnDto(BoardColumn c) {
        return new ColumnDto(c.getId(), c.getBoardId(), c.getName(), c.getPosition());
    }

    static TaskFieldValueDto toTaskFieldDto(FieldDefinition def, String value) {
        return new TaskFieldValueDto(
                def.getId(),
                def.getName(),
                def.getFieldType(),
                def.isRequired(),
                def.isEditable(),
                def.getVisibility(),
                value,
                List.copyOf(def.getRequiredColumnNames()),
                def.getTaskId() != null
        );
    }
}
