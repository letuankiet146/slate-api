package com.slatevn.service;

import com.slatevn.domain.Board;
import com.slatevn.domain.BoardColumn;
import com.slatevn.domain.FieldDefinition;
import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.Task;
import com.slatevn.domain.TaskFieldValue;
import com.slatevn.domain.TaskTemplate;
import com.slatevn.dto.CreateTaskRequest;
import com.slatevn.dto.MoveTaskRequest;
import com.slatevn.dto.TaskDto;
import com.slatevn.dto.TaskFieldValueDto;
import com.slatevn.dto.UpdateTaskRequest;
import com.slatevn.repository.BoardColumnRepository;
import com.slatevn.repository.BoardRepository;
import com.slatevn.repository.FieldDefinitionRepository;
import com.slatevn.repository.TaskFieldValueRepository;
import com.slatevn.repository.TaskRepository;
import com.slatevn.repository.TaskTemplateRepository;
import com.slatevn.repository.WorkspaceRepository;
import com.slatevn.web.BadRequestException;
import com.slatevn.web.ForbiddenException;
import com.slatevn.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final BoardRepository boardRepository;
    private final WorkspaceRepository workspaceRepository;
    private final BoardColumnRepository columnRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final TaskFieldValueRepository taskFieldValueRepository;
    private final TaskTemplateRepository templateRepository;
    private final AuthorizationService authorizationService;

    public TaskService(
            TaskRepository taskRepository,
            BoardRepository boardRepository,
            WorkspaceRepository workspaceRepository,
            BoardColumnRepository columnRepository,
            FieldDefinitionRepository fieldDefinitionRepository,
            TaskFieldValueRepository taskFieldValueRepository,
            TaskTemplateRepository templateRepository,
            AuthorizationService authorizationService
    ) {
        this.taskRepository = taskRepository;
        this.boardRepository = boardRepository;
        this.workspaceRepository = workspaceRepository;
        this.columnRepository = columnRepository;
        this.fieldDefinitionRepository = fieldDefinitionRepository;
        this.taskFieldValueRepository = taskFieldValueRepository;
        this.templateRepository = templateRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public TaskDto create(UUID actorId, UUID boardId, CreateTaskRequest request) {
        ensureBoard(boardId);
        authorizationService.requireBoardPermission(actorId, boardId, PermissionCodes.TASK_CREATE);

        TaskTemplate template = templateRepository.findById(request.templateId())
                .orElseThrow(() -> new BadRequestException("Invalid template for board"));
        if (!template.getVisibleBoardIds().contains(boardId)) {
            throw new BadRequestException("Template is not available on this board");
        }

        UUID columnId = request.columnId();
        if (columnId == null) {
            columnId = columnRepository.findByBoardIdOrderByPositionAsc(boardId).stream()
                    .findFirst()
                    .map(BoardColumn::getId)
                    .orElseThrow(() -> new BadRequestException("Board has no columns"));
        } else {
            columnRepository.findByIdAndBoardId(columnId, boardId)
                    .orElseThrow(() -> new BadRequestException("Invalid column for board"));
        }

        Task task = new Task();
        task.setBoardId(boardId);
        task.setColumnId(columnId);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setCreatedBy(actorId);
        task.setAssigneeId(request.assigneeId());
        task.setTemplateId(template.getId());
        task.setPosition(taskRepository.findByColumnIdAndDeletedAtIsNullOrderByPositionAsc(columnId).size());
        taskRepository.save(task);

        if (request.fieldValues() != null && !request.fieldValues().isEmpty()) {
            applyFieldValues(actorId, boardId, task.getId(), task.getTemplateId(), request.fieldValues(), true);
        }

        return toDto(actorId, task);
    }

    @Transactional
    public TaskDto update(UUID actorId, UUID taskId, UpdateTaskRequest request) {
        Task task = requireActiveTask(taskId);
        authorizationService.requireBoardPermission(actorId, task.getBoardId(), PermissionCodes.TASK_UPDATE);

        if (request.title() != null && !request.title().isBlank()) {
            task.setTitle(request.title());
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        task.setAssigneeId(request.assigneeId());
        taskRepository.save(task);

        if (request.fieldValues() != null && !request.fieldValues().isEmpty()) {
            applyFieldValues(actorId, task.getBoardId(), task.getId(), task.getTemplateId(), request.fieldValues(), false);
        }

        return toDto(actorId, task);
    }

    @Transactional
    public TaskDto move(UUID actorId, UUID taskId, MoveTaskRequest request) {
        Task task = requireActiveTask(taskId);
        authorizationService.requireBoardPermission(actorId, task.getBoardId(), PermissionCodes.TASK_UPDATE);

        columnRepository.findByIdAndBoardId(request.columnId(), task.getBoardId())
                .orElseThrow(() -> new BadRequestException("Invalid column for board"));

        UUID sourceColumnId = task.getColumnId();
        if (!request.columnId().equals(sourceColumnId)) {
            assertRequiredFieldsFilled(task, request.columnId());
        }

        int targetPosition = request.position() != null
                ? request.position()
                : taskRepository.findByColumnIdAndDeletedAtIsNullOrderByPositionAsc(request.columnId()).stream()
                .filter(t -> !t.getId().equals(taskId))
                .toList()
                .size();

        reorderTask(task, request.columnId(), targetPosition);
        return toDto(actorId, task);
    }

    @Transactional(readOnly = true)
    public TaskDto get(UUID actorId, UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (task.isDeleted()) {
            throw new NotFoundException("Task not found");
        }
        if (!authorizationService.hasBoardPermission(actorId, task.getBoardId(), PermissionCodes.TASK_VIEW)
                && !authorizationService.hasBoardPermission(actorId, task.getBoardId(), PermissionCodes.TASK_VIEW_PUBLIC)
                && !authorizationService.hasBoardPermission(actorId, task.getBoardId(), PermissionCodes.BOARD_MANAGE)) {
            throw new ForbiddenException("No access to task");
        }
        return toDto(actorId, task);
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listDeleted(UUID actorId, UUID boardId) {
        ensureBoard(boardId);
        authorizationService.requireBoardPermission(actorId, boardId, PermissionCodes.BOARD_MANAGE);
        return taskRepository.findByBoardIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(boardId).stream()
                .map(task -> toDto(actorId, task))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listDeletedInWorkspace(UUID actorId, UUID workspaceId, List<UUID> boardIdsFilter) {
        ensureWorkspace(workspaceId);

        List<UUID> manageableBoardIds = boardRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(Board::getId)
                .filter(id -> authorizationService.hasBoardPermission(actorId, id, PermissionCodes.BOARD_MANAGE))
                .toList();

        if (boardIdsFilter != null && !boardIdsFilter.isEmpty()) {
            manageableBoardIds = manageableBoardIds.stream()
                    .filter(boardIdsFilter::contains)
                    .toList();
        }

        if (manageableBoardIds.isEmpty()) {
            return List.of();
        }

        return taskRepository.findByBoardIdInAndDeletedAtIsNotNullOrderByDeletedAtDesc(manageableBoardIds).stream()
                .map(task -> toDto(actorId, task))
                .toList();
    }

    @Transactional
    public TaskDto restore(UUID actorId, UUID taskId, UUID targetBoardId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (!task.isDeleted()) {
            throw new BadRequestException("Task is not deleted");
        }

        Board targetBoard = boardRepository.findById(targetBoardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        authorizationService.requireBoardPermission(actorId, targetBoardId, PermissionCodes.BOARD_MANAGE);
        authorizationService.requireBoardPermission(actorId, task.getBoardId(), PermissionCodes.BOARD_MANAGE);

        Board sourceBoard = boardRepository.findById(task.getBoardId())
                .orElseThrow(() -> new NotFoundException("Board not found"));
        if (!sourceBoard.getWorkspaceId().equals(targetBoard.getWorkspaceId())) {
            throw new BadRequestException("Cannot restore task to a board in another workspace");
        }

        if (task.getTemplateId() != null) {
            TaskTemplate template = templateRepository.findById(task.getTemplateId())
                    .orElseThrow(() -> new BadRequestException("Task template no longer exists"));
            if (!template.getVisibleBoardIds().contains(targetBoardId)) {
                template.getVisibleBoardIds().add(targetBoardId);
                templateRepository.save(template);
            }
        }

        BoardColumn todoColumn = columnRepository.findByBoardIdOrderByPositionAsc(targetBoardId).stream()
                .filter(c -> BoardService.DEFAULT_TODO_COLUMN_NAME.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Target board has no \"" + BoardService.DEFAULT_TODO_COLUMN_NAME + "\" column"));

        task.setDeletedAt(null);
        task.setDeletedBy(null);
        task.setBoardId(targetBoardId);
        task.setColumnId(todoColumn.getId());
        task.setPosition(taskRepository.findByColumnIdAndDeletedAtIsNullOrderByPositionAsc(todoColumn.getId()).size());
        taskRepository.save(task);

        return toDto(actorId, task);
    }

    @Transactional
    public List<TaskDto> restoreMany(UUID actorId, List<UUID> taskIds, UUID targetBoardId) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BadRequestException("taskIds required");
        }
        List<UUID> uniqueIds = taskIds.stream().distinct().toList();
        List<TaskDto> restored = new ArrayList<>();
        for (UUID taskId : uniqueIds) {
            restored.add(restore(actorId, taskId, targetBoardId));
        }
        return restored;
    }

    @Transactional
    public void softDelete(UUID actorId, UUID taskId) {
        Task task = requireActiveTask(taskId);
        authorizationService.requireBoardPermission(actorId, task.getBoardId(), PermissionCodes.TASK_UPDATE);

        UUID columnId = task.getColumnId();
        task.setDeletedAt(Instant.now());
        task.setDeletedBy(actorId);
        taskRepository.save(task);

        if (columnId != null) {
            List<Task> remaining = taskRepository.findByColumnIdAndDeletedAtIsNullOrderByPositionAsc(columnId);
            reindex(remaining);
        }
    }

    @Transactional
    public void permanentDelete(UUID actorId, UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (!task.isDeleted()) {
            throw new BadRequestException("Only deleted tasks can be permanently removed");
        }
        authorizationService.requireBoardPermission(actorId, task.getBoardId(), PermissionCodes.BOARD_MANAGE);

        taskFieldValueRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);
    }

    private Task requireActiveTask(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (task.isDeleted()) {
            throw new NotFoundException("Task not found");
        }
        return task;
    }

    private void reorderTask(Task task, UUID targetColumnId, int targetPosition) {
        UUID sourceColumnId = task.getColumnId();
        List<Task> sourceTasks = taskRepository.findByColumnIdAndDeletedAtIsNullOrderByPositionAsc(sourceColumnId).stream()
                .filter(t -> !t.getId().equals(task.getId()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (sourceColumnId.equals(targetColumnId)) {
            int clamped = Math.max(0, Math.min(targetPosition, sourceTasks.size()));
            sourceTasks.add(clamped, task);
            task.setColumnId(targetColumnId);
            reindex(sourceTasks);
            return;
        }

        reindex(sourceTasks);

        List<Task> targetTasks = new ArrayList<>(
                taskRepository.findByColumnIdAndDeletedAtIsNullOrderByPositionAsc(targetColumnId));
        int clamped = Math.max(0, Math.min(targetPosition, targetTasks.size()));
        task.setColumnId(targetColumnId);
        targetTasks.add(clamped, task);
        reindex(targetTasks);
    }

    private void reindex(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            t.setPosition(i);
            taskRepository.save(t);
        }
    }

    private void applyFieldValues(
            UUID actorId,
            UUID boardId,
            UUID taskId,
            UUID templateId,
            Map<UUID, String> values,
            boolean creating
    ) {
        Map<UUID, FieldDefinition> defs = fieldDefinitionRepository
                .findApplicableToTask(taskId, templateId).stream()
                .collect(Collectors.toMap(FieldDefinition::getId, f -> f));

        for (Map.Entry<UUID, String> entry : values.entrySet()) {
            FieldDefinition def = defs.get(entry.getKey());
            if (def == null) {
                throw new BadRequestException("Unknown field: " + entry.getKey());
            }
            if (!creating && !authorizationService.canEditField(actorId, boardId, def)) {
                throw new ForbiddenException("Field is readonly: " + def.getName());
            }
            if (creating && !def.isEditable()
                    && !authorizationService.hasBoardPermission(actorId, boardId, PermissionCodes.BOARD_MANAGE)) {
                throw new ForbiddenException("Field is readonly: " + def.getName());
            }

            TaskFieldValue existing = taskFieldValueRepository
                    .findByTaskIdAndFieldDefinitionId(taskId, def.getId())
                    .orElseGet(() -> {
                        TaskFieldValue v = new TaskFieldValue();
                        v.setTaskId(taskId);
                        v.setFieldDefinitionId(def.getId());
                        return v;
                    });
            existing.setValue(entry.getValue());
            taskFieldValueRepository.save(existing);
        }
    }

    private void assertRequiredFieldsFilled(Task task, UUID targetColumnId) {
        BoardColumn target = columnRepository.findById(targetColumnId)
                .orElseThrow(() -> new BadRequestException("Invalid column for board"));
        List<FieldDefinition> defs = fieldDefinitionRepository.findApplicableToTask(
                task.getId(), task.getTemplateId());
        Map<UUID, String> values = new HashMap<>();
        for (TaskFieldValue v : taskFieldValueRepository.findByTaskId(task.getId())) {
            values.put(v.getFieldDefinitionId(), v.getValue());
        }
        List<String> missing = new ArrayList<>();
        for (FieldDefinition def : defs) {
            if (!def.isRequiredForColumnName(target.getName())) {
                continue;
            }
            String val = values.get(def.getId());
            if (val == null || val.isBlank()) {
                missing.add(def.getName());
            }
        }
        if (!missing.isEmpty()) {
            throw new BadRequestException("Required fields missing before status change: " + String.join(", ", missing));
        }
    }

    private TaskDto toDto(UUID actorId, Task task) {
        List<FieldDefinition> defs = fieldDefinitionRepository.findApplicableToTask(
                task.getId(), task.getTemplateId());
        Map<UUID, String> values = taskFieldValueRepository.findByTaskId(task.getId()).stream()
                .collect(Collectors.toMap(TaskFieldValue::getFieldDefinitionId, TaskFieldValue::getValue, (a, b) -> a));

        List<TaskFieldValueDto> fields = new ArrayList<>();
        for (FieldDefinition def : defs) {
            if (!authorizationService.canViewField(actorId, task.getBoardId(), def)) {
                continue;
            }
            fields.add(BoardService.toTaskFieldDto(def, values.get(def.getId())));
        }

        String templateName = null;
        if (task.getTemplateId() != null) {
            templateName = templateRepository.findById(task.getTemplateId())
                    .map(TaskTemplate::getName)
                    .orElse(null);
        }

        return new TaskDto(
                task.getId(),
                task.getBoardId(),
                task.getColumnId(),
                task.getTitle(),
                task.getDescription(),
                task.getCreatedBy(),
                task.getAssigneeId(),
                task.getTemplateId(),
                templateName,
                task.getPosition(),
                fields,
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getDeletedAt()
        );
    }

    private void ensureBoard(UUID boardId) {
        if (!boardRepository.existsById(boardId)) {
            throw new NotFoundException("Board not found");
        }
    }

    private void ensureWorkspace(UUID workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new NotFoundException("Workspace not found");
        }
    }
}
