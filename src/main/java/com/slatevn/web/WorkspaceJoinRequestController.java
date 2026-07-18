package com.slatevn.web;

import com.slatevn.dto.WorkspaceJoinRequestDto;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.WorkspaceJoinRequestService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspace-join-requests")
public class WorkspaceJoinRequestController {

    private final WorkspaceJoinRequestService joinRequestService;

    public WorkspaceJoinRequestController(WorkspaceJoinRequestService joinRequestService) {
        this.joinRequestService = joinRequestService;
    }

    @PostMapping("/{id}/approve")
    public WorkspaceJoinRequestDto approve(@PathVariable UUID id) {
        return joinRequestService.approve(SecurityUtils.currentUser().getId(), id);
    }

    @PostMapping("/{id}/reject")
    public WorkspaceJoinRequestDto reject(@PathVariable UUID id) {
        return joinRequestService.reject(SecurityUtils.currentUser().getId(), id);
    }
}
