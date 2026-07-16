package com.slatevn.service;

import com.slatevn.domain.Board;
import com.slatevn.domain.FieldDefinition;
import com.slatevn.domain.FieldVisibility;
import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.TaskTemplate;
import com.slatevn.dto.FieldDefinitionDto;
import com.slatevn.dto.SaveTaskTemplateRequest;
import com.slatevn.dto.TaskTemplateDto;
import com.slatevn.dto.TemplateFieldRequest;
import com.slatevn.repository.BoardRepository;
import com.slatevn.repository.FieldDefinitionRepository;
import com.slatevn.repository.TaskRepository;
import com.slatevn.repository.TaskTemplateRepository;
import com.slatevn.repository.WorkspaceRepository;
import com.slatevn.web.BadRequestException;
import com.slatevn.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskTemplateService {

    public static final String DEFAULT_TEMPLATE_NAME = "Mặc định";

    private final TaskTemplateRepository templateRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AuthorizationService authorizationService;

    public TaskTemplateService(
            TaskTemplateRepository templateRepository,
            FieldDefinitionRepository fieldDefinitionRepository,
            BoardRepository boardRepository,
            TaskRepository taskRepository,
            WorkspaceRepository workspaceRepository,
            AuthorizationService authorizationService
    ) {
        this.templateRepository = templateRepository;
        this.fieldDefinitionRepository = fieldDefinitionRepository;
        this.boardRepository = boardRepository;
        this.taskRepository = taskRepository;
        this.workspaceRepository = workspaceRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<TaskTemplateDto> listByWorkspace(UUID actorId, UUID workspaceId) {
        ensureWorkspace(workspaceId);
        requireWorkspaceView(actorId, workspaceId);
        return templateRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskTemplateDto> listVisibleOnBoard(UUID actorId, UUID boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
        authorizationService.requireBoardPermission(actorId, boardId, PermissionCodes.TASK_VIEW);
        return templateRepository.findVisibleOnBoard(boardId).stream()
                .filter(t -> t.getWorkspaceId().equals(board.getWorkspaceId()))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskTemplateDto get(UUID actorId, UUID workspaceId, UUID templateId) {
        ensureWorkspace(workspaceId);
        requireWorkspaceView(actorId, workspaceId);
        TaskTemplate template = templateRepository.findByIdAndWorkspaceId(templateId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template not found"));
        return toDto(template);
    }

    @Transactional
    public TaskTemplateDto create(UUID actorId, UUID workspaceId, SaveTaskTemplateRequest request) {
        ensureWorkspace(workspaceId);
        authorizationService.requireWorkspacePermission(actorId, workspaceId, PermissionCodes.BOARD_MANAGE);

        String name = request.name().trim();
        if (templateRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
            throw new BadRequestException("Template name already exists");
        }
        assertUniquePattern(workspaceId, request.fields(), null);

        TaskTemplate template = new TaskTemplate();
        template.setWorkspaceId(workspaceId);
        template.setName(name);
        template.setVisibleBoardIds(validateVisibleBoards(workspaceId, request.visibleBoardIds()));
        templateRepository.save(template);
        replaceFields(template.getId(), request.fields());
        return toDto(template);
    }

    @Transactional
    public TaskTemplateDto update(UUID actorId, UUID workspaceId, UUID templateId, SaveTaskTemplateRequest request) {
        ensureWorkspace(workspaceId);
        authorizationService.requireWorkspacePermission(actorId, workspaceId, PermissionCodes.BOARD_MANAGE);

        TaskTemplate template = templateRepository.findByIdAndWorkspaceId(templateId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template not found"));

        String name = request.name().trim();
        if (templateRepository.existsByWorkspaceIdAndNameIgnoreCaseAndIdNot(workspaceId, name, templateId)) {
            throw new BadRequestException("Template name already exists");
        }
        assertUniquePattern(workspaceId, request.fields(), templateId);

        template.setName(name);
        template.setVisibleBoardIds(validateVisibleBoards(workspaceId, request.visibleBoardIds()));
        templateRepository.save(template);
        replaceFields(template.getId(), request.fields());
        return toDto(template);
    }

    @Transactional
    public void delete(UUID actorId, UUID workspaceId, UUID templateId) {
        ensureWorkspace(workspaceId);
        authorizationService.requireWorkspacePermission(actorId, workspaceId, PermissionCodes.BOARD_MANAGE);

        TaskTemplate template = templateRepository.findByIdAndWorkspaceId(templateId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template not found"));

        if (DEFAULT_TEMPLATE_NAME.equalsIgnoreCase(template.getName())) {
            throw new BadRequestException("Cannot delete the default template");
        }
        if (taskRepository.existsByTemplateId(templateId)) {
            throw new BadRequestException("Cannot delete template that is used by tasks");
        }

        fieldDefinitionRepository.deleteByTemplateId(templateId);
        templateRepository.delete(template);
    }

    @Transactional
    public TaskTemplate ensureDefaultTemplate(UUID workspaceId) {
        return templateRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, DEFAULT_TEMPLATE_NAME)
                .orElseGet(() -> {
                    TaskTemplate template = new TaskTemplate();
                    template.setWorkspaceId(workspaceId);
                    template.setName(DEFAULT_TEMPLATE_NAME);
                    template.setVisibleBoardIds(new HashSet<>());
                    return templateRepository.save(template);
                });
    }

    @Transactional
    public void linkBoardToDefaultTemplate(UUID workspaceId, UUID boardId) {
        TaskTemplate template = ensureDefaultTemplate(workspaceId);
        Set<UUID> boards = new HashSet<>(template.getVisibleBoardIds());
        boards.add(boardId);
        template.setVisibleBoardIds(boards);
        templateRepository.save(template);
    }

    TaskTemplateDto toDto(TaskTemplate template) {
        List<FieldDefinitionDto> fields = fieldDefinitionRepository
                .findByTemplateIdOrderByPositionAsc(template.getId()).stream()
                .map(this::toFieldDto)
                .toList();
        return new TaskTemplateDto(
                template.getId(),
                template.getWorkspaceId(),
                template.getName(),
                fields,
                List.copyOf(template.getVisibleBoardIds()),
                template.getCreatedAt()
        );
    }

    private void replaceFields(UUID templateId, List<TemplateFieldRequest> fields) {
        List<FieldDefinition> existing = fieldDefinitionRepository.findByTemplateIdOrderByPositionAsc(templateId);
        Set<UUID> keepIds = fields.stream()
                .map(TemplateFieldRequest::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (FieldDefinition def : existing) {
            if (!keepIds.contains(def.getId())) {
                fieldDefinitionRepository.delete(def);
            }
        }

        int position = 0;
        for (TemplateFieldRequest request : fields) {
            FieldDefinition field;
            if (request.id() != null) {
                field = existing.stream()
                        .filter(f -> f.getId().equals(request.id()))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException("Unknown field: " + request.id()));
                if (!templateId.equals(field.getTemplateId())) {
                    throw new BadRequestException("Field does not belong to template");
                }
            } else {
                field = new FieldDefinition();
                field.setTemplateId(templateId);
                field.setBoardId(null);
            }
            field.setName(request.name().trim());
            field.setFieldType(request.fieldType());
            field.setRequired(request.required());
            field.setEditable(request.editable());
            field.setVisibility(request.visibility() != null ? request.visibility() : FieldVisibility.INTERNAL);
            field.setPosition(position++);
            field.setRequiredColumnNames(toRequiredNameSet(request.requiredColumnNames()));
            fieldDefinitionRepository.save(field);
        }
    }

    private void assertUniquePattern(UUID workspaceId, List<TemplateFieldRequest> fields, UUID excludeTemplateId) {
        String signature = patternSignature(fields);
        for (TaskTemplate other : templateRepository.findByWorkspaceIdOrderByNameAsc(workspaceId)) {
            if (excludeTemplateId != null && other.getId().equals(excludeTemplateId)) {
                continue;
            }
            List<FieldDefinition> otherFields = fieldDefinitionRepository.findByTemplateIdOrderByPositionAsc(other.getId());
            if (signature.equals(patternSignatureFromDefs(otherFields))) {
                throw new BadRequestException("Template pattern already exists: " + other.getName());
            }
        }
    }

    private String patternSignature(List<TemplateFieldRequest> fields) {
        List<TemplateFieldRequest> required = fields.stream()
                .filter(TemplateFieldRequest::required)
                .sorted(Comparator
                        .comparing((TemplateFieldRequest f) -> f.name().trim().toLowerCase(Locale.ROOT))
                        .thenComparing(f -> f.fieldType().name())
                        .thenComparing(f -> normalizedColumnNames(f.requiredColumnNames())))
                .toList();
        String requiredPart = required.stream()
                .map(f -> f.name().trim().toLowerCase(Locale.ROOT)
                        + "|" + f.fieldType().name()
                        + "|" + normalizedColumnNames(f.requiredColumnNames()))
                .collect(Collectors.joining(";"));
        return fields.size() + "#" + requiredPart;
    }

    private String patternSignatureFromDefs(List<FieldDefinition> fields) {
        List<FieldDefinition> required = fields.stream()
                .filter(FieldDefinition::isRequired)
                .sorted(Comparator
                        .comparing((FieldDefinition f) -> f.getName().trim().toLowerCase(Locale.ROOT))
                        .thenComparing(f -> f.getFieldType().name())
                        .thenComparing(f -> normalizedColumnNames(new ArrayList<>(f.getRequiredColumnNames()))))
                .toList();
        String requiredPart = required.stream()
                .map(f -> f.getName().trim().toLowerCase(Locale.ROOT)
                        + "|" + f.getFieldType().name()
                        + "|" + normalizedColumnNames(new ArrayList<>(f.getRequiredColumnNames())))
                .collect(Collectors.joining(";"));
        return fields.size() + "#" + requiredPart;
    }

    private String normalizedColumnNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "*";
        }
        return names.stream()
                .map(n -> n.trim().toLowerCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining(","));
    }

    private Set<UUID> validateVisibleBoards(UUID workspaceId, List<UUID> boardIds) {
        if (boardIds == null) {
            return new HashSet<>();
        }
        Set<UUID> valid = boardRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(workspaceId).stream()
                .map(Board::getId)
                .collect(Collectors.toSet());
        Set<UUID> selected = new HashSet<>();
        for (UUID boardId : boardIds) {
            if (!valid.contains(boardId)) {
                throw new BadRequestException("Invalid board for workspace: " + boardId);
            }
            selected.add(boardId);
        }
        return selected;
    }

    private Set<String> toRequiredNameSet(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new HashSet<>();
        }
        return names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private FieldDefinitionDto toFieldDto(FieldDefinition f) {
        return new FieldDefinitionDto(
                f.getId(),
                f.getBoardId(),
                f.getTaskId(),
                f.getTemplateId(),
                f.getName(),
                f.getFieldType(),
                f.isRequired(),
                f.isEditable(),
                f.getVisibility(),
                f.getPosition(),
                List.copyOf(f.getRequiredColumnNames())
        );
    }

    private void ensureWorkspace(UUID workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new NotFoundException("Workspace not found");
        }
    }

    private void requireWorkspaceView(UUID actorId, UUID workspaceId) {
        if (!authorizationService.hasWorkspacePermission(actorId, workspaceId, PermissionCodes.TASK_VIEW)
                && !authorizationService.hasWorkspacePermission(actorId, workspaceId, PermissionCodes.BOARD_MANAGE)
                && !authorizationService.hasWorkspacePermission(actorId, workspaceId, PermissionCodes.WORKSPACE_MANAGE)
                && !authorizationService.hasWorkspacePermission(actorId, workspaceId, PermissionCodes.TASK_VIEW_PUBLIC)) {
            // allow if user can see any board in workspace
            boolean canSeeBoard = boardRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(workspaceId).stream()
                    .anyMatch(b -> authorizationService.hasBoardPermission(actorId, b.getId(), PermissionCodes.TASK_VIEW)
                            || authorizationService.hasBoardPermission(actorId, b.getId(), PermissionCodes.BOARD_MANAGE));
            if (!canSeeBoard) {
                throw new com.slatevn.web.ForbiddenException("No access to workspace");
            }
        }
    }
}
