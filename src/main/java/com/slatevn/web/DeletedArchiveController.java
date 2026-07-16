package com.slatevn.web;

import com.slatevn.dto.WorkspaceDto;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.WorkspaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deleted")
public class DeletedArchiveController {

    private final WorkspaceService workspaceService;

    public DeletedArchiveController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/workspaces")
    public List<WorkspaceDto> listDeletedWorkspaces() {
        return workspaceService.listDeleted(SecurityUtils.currentUser().getId());
    }
}
