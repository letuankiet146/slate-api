package com.slatevn.service;

import com.slatevn.domain.Board;
import com.slatevn.domain.BoardColumn;
import com.slatevn.domain.FieldDefinition;
import com.slatevn.domain.Membership;
import com.slatevn.domain.RoleCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.Task;
import com.slatevn.domain.TaskFieldValue;
import com.slatevn.domain.TaskTemplate;
import com.slatevn.dto.MoveTaskRequest;
import com.slatevn.dto.MyTasksColumnDto;
import com.slatevn.dto.MyTasksMoveRequest;
import com.slatevn.dto.MyTasksTaskDto;
import com.slatevn.dto.MyTasksViewDto;
import com.slatevn.dto.TaskFieldValueDto;
import com.slatevn.repository.BoardColumnRepository;
import com.slatevn.repository.BoardRepository;
import com.slatevn.repository.FieldDefinitionRepository;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.TaskFieldValueRepository;
import com.slatevn.repository.TaskRepository;
import com.slatevn.repository.TaskTemplateRepository;
import com.slatevn.web.BadRequestException;
import com.slatevn.web.ForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MyTasksService {

    private final TaskRepository taskRepository;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final TaskFieldValueRepository taskFieldValueRepository;
    private final TaskTemplateRepository templateRepository;
    private final MembershipRepository membershipRepository;
    private final AuthorizationService authorizationService;
    private final TaskService taskService;

    public MyTasksService(
            TaskRepository taskRepository,
            BoardRepository boardRepository,
            BoardColumnRepository columnRepository,
            FieldDefinitionRepository fieldDefinitionRepository,
            TaskFieldValueRepository taskFieldValueRepository,
            TaskTemplateRepository templateRepository,
            MembershipRepository membershipRepository,
            AuthorizationService authorizationService,
            TaskService taskService
    ) {
        this.taskRepository = taskRepository;
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.fieldDefinitionRepository = fieldDefinitionRepository;
        this.taskFieldValueRepository = taskFieldValueRepository;
        this.templateRepository = templateRepository;
        this.membershipRepository = membershipRepository;
        this.authorizationService = authorizationService;
        this.taskService = taskService;
    }

    @Transactional(readOnly = true)
    public MyTasksViewDto getView(UUID actorId) {
        if (!authorizationService.canAccessMyTasks(actorId)) {
            throw new ForbiddenException("My tasks view is not available for this account");
        }

        List<UUID> accessibleBoardIds = accessibleBoardIds(actorId);
        Map<String, ColumnAggregate> columnAggregates = new LinkedHashMap<>();
        for (UUID boardId : accessibleBoardIds) {
            for (BoardColumn column : columnRepository.findByBoardIdOrderByPositionAsc(boardId)) {
                columnAggregates.computeIfAbsent(column.getName(), name -> new ColumnAggregate(name, column.getPosition()))
                        .position = Math.min(columnAggregates.get(column.getName()).position, column.getPosition());
            }
        }

        List<MyTasksColumnDto> columns = columnAggregates.values().stream()
                .sorted(Comparator.comparingInt(c -> c.position))
                .map(c -> new MyTasksColumnDto(c.name, c.name, c.position))
                .toList();

        List<Task> assignedTasks = taskRepository.findByAssigneeId(actorId).stream()
                .filter(task -> !task.isDeleted())
                .filter(task -> accessibleBoardIds.contains(task.getBoardId()))
                .filter(task -> authorizationService.canViewTask(actorId, task))
                .toList();

        List<UUID> taskIds = assignedTasks.stream().map(Task::getId).toList();
        Map<UUID, List<TaskFieldValue>> valuesByTask = taskIds.isEmpty()
                ? Map.of()
                : taskFieldValueRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.groupingBy(TaskFieldValue::getTaskId));

        Map<UUID, String> boardNames = accessibleBoardIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> boardRepository.findById(id).map(Board::getName).orElse("Board")
                ));

        List<MyTasksTaskDto> taskDtos = new ArrayList<>();
        for (Task task : assignedTasks) {
            BoardColumn column = columnRepository.findById(task.getColumnId()).orElse(null);
            String columnName = column != null ? column.getName() : "Unknown";
            taskDtos.add(toTaskDto(actorId, task, boardNames.get(task.getBoardId()), columnName, valuesByTask));
        }

        boolean readOnly = !authorizationService.hasWorkspaceOrBoardMemberRole(actorId)
                && !authorizationService.hasAnyBoardAdminRole(actorId);

        return new MyTasksViewDto(columns, taskDtos, readOnly);
    }

    @Transactional
    public MyTasksTaskDto move(UUID actorId, UUID taskId, MyTasksMoveRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BadRequestException("Task not found"));
        if (!actorId.equals(task.getAssigneeId())) {
            throw new ForbiddenException("Cannot move tasks that are not assigned to you");
        }
        if (!authorizationService.canUpdateTask(actorId, task)) {
            throw new ForbiddenException("Cannot move task");
        }

        BoardColumn targetColumn = columnRepository.findByBoardIdOrderByPositionAsc(task.getBoardId()).stream()
                .filter(c -> c.getName().equals(request.columnName()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Column not found on task board: " + request.columnName()));

        taskService.move(actorId, taskId, new MoveTaskRequest(targetColumn.getId(), request.position()));

        Task moved = taskRepository.findById(taskId).orElseThrow();
        String boardName = boardRepository.findById(moved.getBoardId()).map(Board::getName).orElse("Board");
        Map<UUID, List<TaskFieldValue>> valuesByTask = taskFieldValueRepository.findByTaskIdIn(List.of(taskId))
                .stream()
                .collect(Collectors.groupingBy(TaskFieldValue::getTaskId));
        return toTaskDto(actorId, moved, boardName, targetColumn.getName(), valuesByTask);
    }

    private List<UUID> accessibleBoardIds(UUID actorId) {
        Set<UUID> boardIds = new LinkedHashSet<>();
        membershipRepository.findByUserId(actorId).stream()
                .filter(m -> m.getScopeType() == ScopeType.BOARD && m.getBoardId() != null)
                .filter(m -> RoleCodes.BOARD_MEMBER.equals(m.getRole().getCode())
                        || RoleCodes.BOARD_VIEWER.equals(m.getRole().getCode()))
                .map(Membership::getBoardId)
                .forEach(boardIds::add);
        taskRepository.findByAssigneeId(actorId).stream()
                .filter(task -> !task.isDeleted())
                .map(Task::getBoardId)
                .forEach(boardIds::add);
        return new ArrayList<>(boardIds);
    }

    private MyTasksTaskDto toTaskDto(
            UUID actorId,
            Task task,
            String boardName,
            String columnName,
            Map<UUID, List<TaskFieldValue>> valuesByTask
    ) {
        Map<UUID, String> values = valuesByTask.getOrDefault(task.getId(), List.of()).stream()
                .collect(Collectors.toMap(TaskFieldValue::getFieldDefinitionId, TaskFieldValue::getValue, (a, b) -> a));

        List<FieldDefinition> fields = fieldDefinitionRepository.findApplicableToTask(task.getId(), task.getTemplateId());

        List<TaskFieldValueDto> fieldDtos = new ArrayList<>();
        for (FieldDefinition def : fields) {
            if (!authorizationService.canViewField(actorId, task.getBoardId(), def)) {
                continue;
            }
            fieldDtos.add(BoardService.toTaskFieldDto(def, values.get(def.getId())));
        }

        TaskTemplate template = task.getTemplateId() != null
                ? templateRepository.findById(task.getTemplateId()).orElse(null)
                : null;

        return new MyTasksTaskDto(
                task.getId(),
                task.getBoardId(),
                boardName,
                task.getColumnId(),
                columnName,
                task.getTitle(),
                task.getDescription(),
                task.getAssigneeId(),
                task.getTemplateId(),
                template != null ? template.getName() : null,
                task.getPosition(),
                fieldDtos,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private static final class ColumnAggregate {
        private final String name;
        private int position;

        private ColumnAggregate(String name, int position) {
            this.name = name;
            this.position = position;
        }
    }
}
