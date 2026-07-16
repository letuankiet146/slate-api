package com.slatevn.web;

import com.slatevn.dto.BulkRestoreTasksRequest;
import com.slatevn.dto.MoveTaskRequest;
import com.slatevn.dto.RestoreTaskRequest;
import com.slatevn.dto.TaskDto;
import com.slatevn.dto.UpdateTaskRequest;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{taskId}")
    public TaskDto get(@PathVariable UUID taskId) {
        return taskService.get(SecurityUtils.currentUser().getId(), taskId);
    }

    @PutMapping("/{taskId}")
    public TaskDto update(@PathVariable UUID taskId, @RequestBody UpdateTaskRequest request) {
        return taskService.update(SecurityUtils.currentUser().getId(), taskId, request);
    }

    @PostMapping("/{taskId}/move")
    public TaskDto move(@PathVariable UUID taskId, @Valid @RequestBody MoveTaskRequest request) {
        return taskService.move(SecurityUtils.currentUser().getId(), taskId, request);
    }

    @PostMapping("/restore")
    public List<TaskDto> restoreMany(@Valid @RequestBody BulkRestoreTasksRequest request) {
        return taskService.restoreMany(
                SecurityUtils.currentUser().getId(),
                request.taskIds(),
                request.boardId()
        );
    }

    @PostMapping("/{taskId}/restore")
    public TaskDto restore(@PathVariable UUID taskId, @Valid @RequestBody RestoreTaskRequest request) {
        return taskService.restore(SecurityUtils.currentUser().getId(), taskId, request.boardId());
    }

    @DeleteMapping("/{taskId}")
    public void softDelete(@PathVariable UUID taskId) {
        taskService.softDelete(SecurityUtils.currentUser().getId(), taskId);
    }

    @DeleteMapping("/{taskId}/permanent")
    public void permanentDelete(@PathVariable UUID taskId) {
        taskService.permanentDelete(SecurityUtils.currentUser().getId(), taskId);
    }
}
