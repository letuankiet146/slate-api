package com.slatevn.web;

import com.slatevn.dto.ActivityLogDto;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.ActivityLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping("/workspaces/{workspaceId}/activity")
    public List<ActivityLogDto> workspaceActivity(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) Integer limit
    ) {
        return activityLogService.listForWorkspace(SecurityUtils.currentUser().getId(), workspaceId, limit);
    }

    @GetMapping("/boards/{boardId}/activity")
    public List<ActivityLogDto> boardActivity(
            @PathVariable UUID boardId,
            @RequestParam(required = false) Integer limit
    ) {
        return activityLogService.listForBoard(SecurityUtils.currentUser().getId(), boardId, limit);
    }

    @GetMapping("/tasks/{taskId}/activity")
    public List<ActivityLogDto> taskActivity(
            @PathVariable UUID taskId,
            @RequestParam(required = false) Integer limit
    ) {
        return activityLogService.listForTask(SecurityUtils.currentUser().getId(), taskId, limit);
    }
}
