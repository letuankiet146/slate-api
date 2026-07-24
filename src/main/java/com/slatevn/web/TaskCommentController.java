package com.slatevn.web;

import com.slatevn.dto.CreateTaskCommentRequest;
import com.slatevn.dto.TaskCommentDto;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.TaskCommentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
public class TaskCommentController {

    private final TaskCommentService taskCommentService;

    public TaskCommentController(TaskCommentService taskCommentService) {
        this.taskCommentService = taskCommentService;
    }

    @GetMapping
    public List<TaskCommentDto> list(@PathVariable UUID taskId) {
        return taskCommentService.list(SecurityUtils.currentUser().getId(), taskId);
    }

    @PostMapping
    public TaskCommentDto create(
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateTaskCommentRequest request
    ) {
        return taskCommentService.create(SecurityUtils.currentUser().getId(), taskId, request);
    }

    @PostMapping("/{commentId}/update")
    public TaskCommentDto updateViaPost(
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @Valid @RequestBody CreateTaskCommentRequest request
    ) {
        return taskCommentService.update(SecurityUtils.currentUser().getId(), taskId, commentId, request);
    }

    @PatchMapping("/{commentId}")
    public TaskCommentDto patch(
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @Valid @RequestBody CreateTaskCommentRequest request
    ) {
        return taskCommentService.update(SecurityUtils.currentUser().getId(), taskId, commentId, request);
    }

    @PutMapping("/{commentId}")
    public TaskCommentDto update(
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @Valid @RequestBody CreateTaskCommentRequest request
    ) {
        return taskCommentService.update(SecurityUtils.currentUser().getId(), taskId, commentId, request);
    }
}
