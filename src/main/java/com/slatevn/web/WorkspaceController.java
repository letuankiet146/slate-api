package com.slatevn.web;

import com.slatevn.dto.AddMembershipRequest;
import com.slatevn.dto.BoardDto;
import com.slatevn.dto.CreateWorkspaceRequest;
import com.slatevn.dto.MembershipDto;
import com.slatevn.dto.SaveTaskTemplateRequest;
import com.slatevn.dto.TaskTemplateDto;
import com.slatevn.dto.UpdateWorkspaceRequest;
import com.slatevn.dto.WorkspaceAdminCapabilityDto;
import com.slatevn.dto.WorkspaceDetailDto;
import com.slatevn.dto.WorkspaceDto;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.BoardService;
import com.slatevn.service.TaskTemplateService;
import com.slatevn.service.WorkspaceService;
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
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final TaskTemplateService taskTemplateService;
    private final BoardService boardService;

    public WorkspaceController(
            WorkspaceService workspaceService,
            TaskTemplateService taskTemplateService,
            BoardService boardService
    ) {
        this.workspaceService = workspaceService;
        this.taskTemplateService = taskTemplateService;
        this.boardService = boardService;
    }

    @GetMapping
    public List<WorkspaceDto> list() {
        return workspaceService.list(SecurityUtils.currentUser().getId());
    }

    @GetMapping("/capabilities/workspace-admin")
    public WorkspaceAdminCapabilityDto workspaceAdminCapability() {
        return new WorkspaceAdminCapabilityDto(
                workspaceService.hasAnyWorkspaceAdminRole(SecurityUtils.currentUser().getId())
        );
    }

    @GetMapping("/{id}")
    public WorkspaceDetailDto get(@PathVariable UUID id) {
        return workspaceService.getDetail(SecurityUtils.currentUser().getId(), id);
    }

    @PutMapping("/{id}")
    public WorkspaceDto update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkspaceRequest request
    ) {
        return workspaceService.update(SecurityUtils.currentUser().getId(), id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteWorkspace(@PathVariable UUID id) {
        workspaceService.softDelete(SecurityUtils.currentUser().getId(), id);
    }

    @PostMapping("/{id}/restore")
    public WorkspaceDto restoreWorkspace(@PathVariable UUID id) {
        return workspaceService.restore(SecurityUtils.currentUser().getId(), id);
    }

    @GetMapping("/{id}/deleted-boards")
    public List<BoardDto> listDeletedBoards(@PathVariable UUID id) {
        return boardService.listDeletedInWorkspace(SecurityUtils.currentUser().getId(), id);
    }

    @PostMapping
    public WorkspaceDto create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return workspaceService.create(SecurityUtils.currentUser().getId(), request);
    }

    @GetMapping("/{id}/memberships")
    public List<MembershipDto> memberships(@PathVariable UUID id) {
        return workspaceService.listMemberships(SecurityUtils.currentUser().getId(), id);
    }

    @PostMapping("/{id}/memberships")
    public MembershipDto addMembership(
            @PathVariable UUID id,
            @Valid @RequestBody AddMembershipRequest request
    ) {
        return workspaceService.addMembership(SecurityUtils.currentUser().getId(), id, request);
    }

    @DeleteMapping("/{id}/memberships/{membershipId}")
    public void removeMembership(@PathVariable UUID id, @PathVariable UUID membershipId) {
        workspaceService.removeMembership(SecurityUtils.currentUser().getId(), id, membershipId);
    }

    @GetMapping("/{id}/templates")
    public List<TaskTemplateDto> listTemplates(@PathVariable UUID id) {
        return taskTemplateService.listByWorkspace(SecurityUtils.currentUser().getId(), id);
    }

    @GetMapping("/{id}/templates/{templateId}")
    public TaskTemplateDto getTemplate(@PathVariable UUID id, @PathVariable UUID templateId) {
        return taskTemplateService.get(SecurityUtils.currentUser().getId(), id, templateId);
    }

    @PostMapping("/{id}/templates")
    public TaskTemplateDto createTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody SaveTaskTemplateRequest request
    ) {
        return taskTemplateService.create(SecurityUtils.currentUser().getId(), id, request);
    }

    @PutMapping("/{id}/templates/{templateId}")
    public TaskTemplateDto updateTemplate(
            @PathVariable UUID id,
            @PathVariable UUID templateId,
            @Valid @RequestBody SaveTaskTemplateRequest request
    ) {
        return taskTemplateService.update(SecurityUtils.currentUser().getId(), id, templateId, request);
    }

    @DeleteMapping("/{id}/templates/{templateId}")
    public void deleteTemplate(@PathVariable UUID id, @PathVariable UUID templateId) {
        taskTemplateService.delete(SecurityUtils.currentUser().getId(), id, templateId);
    }
}
