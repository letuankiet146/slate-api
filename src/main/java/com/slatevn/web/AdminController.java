package com.slatevn.web;

import com.slatevn.dto.ProvisionTenantRequest;
import com.slatevn.dto.ProvisionTenantResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @PostMapping("/provision-tenant")
    public ProvisionTenantResponse provisionTenant(@Valid @RequestBody ProvisionTenantRequest request) {
        throw new ForbiddenException("Tenant provisioning is no longer available");
    }
}
