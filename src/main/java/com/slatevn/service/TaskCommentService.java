package com.slatevn.service;

import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.Task;
import com.slatevn.domain.TaskComment;
import com.slatevn.domain.User;
import com.slatevn.dto.CreateTaskCommentRequest;
import com.slatevn.dto.TaskCommentDto;
import com.slatevn.repository.TaskCommentRepository;
import com.slatevn.repository.TaskRepository;
import com.slatevn.repository.UserRepository;
import com.slatevn.web.ForbiddenException;
import com.slatevn.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskCommentService {

    private final TaskCommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final BoardService boardService;
    private final TaskNotificationService taskNotificationService;

    public TaskCommentService(
            TaskCommentRepository commentRepository,
            TaskRepository taskRepository,
            UserRepository userRepository,
            AuthorizationService authorizationService,
            BoardService boardService,
            TaskNotificationService taskNotificationService
    ) {
        this.commentRepository = commentRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.boardService = boardService;
        this.taskNotificationService = taskNotificationService;
    }

    @Transactional(readOnly = true)
    public List<TaskCommentDto> list(UUID actorId, UUID taskId) {
        Task task = requireViewableTask(actorId, taskId);
        requireCanViewTask(actorId, task);
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TaskCommentDto create(UUID actorId, UUID taskId, CreateTaskCommentRequest request) {
        Task task = requireViewableTask(actorId, taskId);
        requireCanParticipate(actorId, task.getBoardId());

        User author = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String body = request.body().trim();

        TaskComment comment = new TaskComment();
        comment.setTaskId(taskId);
        comment.setAuthorId(actorId);
        comment.setAuthorName(author.getDisplayName());
        comment.setBody(body);
        TaskComment saved = commentRepository.save(comment);

        List<TaskNotificationService.MentionCandidate> candidates = boardService.listMembers(actorId, task.getBoardId())
                .stream()
                .map(member -> new TaskNotificationService.MentionCandidate(
                        member.userId(),
                        member.displayName(),
                        member.email()
                ))
                .toList();
        taskNotificationService.notifyMentions(actorId, task, author.getDisplayName(), body, candidates);

        return toDto(saved);
    }

    @Transactional
    public TaskCommentDto update(UUID actorId, UUID taskId, UUID commentId, CreateTaskCommentRequest request) {
        Task task = requireViewableTask(actorId, taskId);
        TaskComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        if (!comment.getTaskId().equals(taskId)) {
            throw new NotFoundException("Comment not found");
        }
        if (!comment.getAuthorId().equals(actorId)) {
            throw new ForbiddenException("Only the author can edit this comment");
        }
        requireCanParticipate(actorId, task.getBoardId());

        String previousBody = comment.getBody();
        String body = request.body().trim();
        comment.setBody(body);
        comment.touch();
        TaskComment saved = commentRepository.save(comment);

        User author = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        List<TaskNotificationService.MentionCandidate> candidates = boardService.listMembers(actorId, task.getBoardId())
                .stream()
                .map(member -> new TaskNotificationService.MentionCandidate(
                        member.userId(),
                        member.displayName(),
                        member.email()
                ))
                .toList();
        Set<UUID> previouslyMentioned = taskNotificationService.parseMentionedUserIds(previousBody, candidates);
        taskNotificationService.notifyMentions(
                actorId,
                task,
                author.getDisplayName(),
                body,
                candidates,
                previouslyMentioned
        );

        return toDto(saved);
    }

    private Task requireViewableTask(UUID actorId, UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (task.isDeleted()) {
            throw new NotFoundException("Task not found");
        }
        return task;
    }

    private void requireCanViewTask(UUID actorId, Task task) {
        if (!authorizationService.hasBoardPermission(actorId, task.getBoardId(), PermissionCodes.TASK_VIEW)
                && !authorizationService.hasBoardPermission(actorId, task.getBoardId(), PermissionCodes.TASK_VIEW_PUBLIC)
                && !authorizationService.hasBoardPermission(actorId, task.getBoardId(), PermissionCodes.BOARD_MANAGE)) {
            throw new ForbiddenException("No access to task");
        }
    }

    private void requireCanParticipate(UUID actorId, UUID boardId) {
        if (!authorizationService.hasBoardPermission(actorId, boardId, PermissionCodes.TASK_VIEW)
                && !authorizationService.hasBoardPermission(actorId, boardId, PermissionCodes.TASK_VIEW_PUBLIC)
                && !authorizationService.hasBoardPermission(actorId, boardId, PermissionCodes.TASK_UPDATE)
                && !authorizationService.hasBoardPermission(actorId, boardId, PermissionCodes.BOARD_MANAGE)) {
            throw new ForbiddenException("No access to task");
        }
    }

    private TaskCommentDto toDto(TaskComment comment) {
        return new TaskCommentDto(
                comment.getId(),
                comment.getTaskId(),
                comment.getAuthorId(),
                comment.getAuthorName(),
                comment.getBody(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
