package com.slatevn.web;

import com.slatevn.dto.ProvisionTenantRequest;
import com.slatevn.dto.ProvisionTenantResponse;
import com.slatevn.dto.WorkspaceDto;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.AdminService;
import com.slatevn.service.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final WorkspaceService workspaceService;
    private final AdminService adminService;

    public AdminController(WorkspaceService workspaceService, AdminService adminService) {
        this.workspaceService = workspaceService;
        this.adminService = adminService;
    }

    @GetMapping("/workspaces")
    public List<WorkspaceDto> listWorkspaces() {
        return workspaceService.listAllForAdmin(SecurityUtils.currentUser().getId());
    }

    @PostMapping("/provision-tenant")
    public ProvisionTenantResponse provisionTenant(@Valid @RequestBody ProvisionTenantRequest request) {
        return adminService.provisionTenant(SecurityUtils.currentUser().getId(), request);
    }
}
