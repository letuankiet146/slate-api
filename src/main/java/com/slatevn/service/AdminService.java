package com.slatevn.service;

import com.slatevn.domain.AccountType;
import com.slatevn.domain.Membership;
import com.slatevn.domain.PermissionCodes;
import com.slatevn.domain.Role;
import com.slatevn.domain.RoleCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.User;
import com.slatevn.domain.Workspace;
import com.slatevn.dto.ProvisionTenantRequest;
import com.slatevn.dto.ProvisionTenantResponse;
import com.slatevn.dto.UserDto;
import com.slatevn.dto.WorkspaceDto;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.RoleRepository;
import com.slatevn.repository.UserRepository;
import com.slatevn.repository.WorkspaceRepository;
import com.slatevn.web.BadRequestException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final AuthorizationService authorizationService;
    private final UserService userService;
    private final TaskTemplateService taskTemplateService;
    private final PasswordEncoder passwordEncoder;

    public AdminService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            MembershipRepository membershipRepository,
            RoleRepository roleRepository,
            AuthorizationService authorizationService,
            UserService userService,
            TaskTemplateService taskTemplateService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.authorizationService = authorizationService;
        this.userService = userService;
        this.taskTemplateService = taskTemplateService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ProvisionTenantResponse provisionTenant(java.util.UUID actorId, ProvisionTenantRequest request) {
        authorizationService.requireSystemPermission(actorId, PermissionCodes.USER_MANAGE);
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("Email already exists");
        }
        if (workspaceRepository.existsByKeyIgnoreCase(request.workspaceKey())) {
            throw new BadRequestException("Workspace key already exists");
        }

        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setLocale(request.locale() != null && !request.locale().isBlank() ? request.locale() : "vi");
        user.setEnabled(true);
        user.setAccountType(AccountType.OWNER);
        userRepository.save(user);

        Workspace workspace = new Workspace();
        workspace.setName(request.workspaceName());
        workspace.setKey(request.workspaceKey().toUpperCase());
        workspace.setCreatedBy(actorId);
        workspace.setOwnerId(user.getId());
        String companyEmail = request.companyEmail();
        if (companyEmail == null || companyEmail.isBlank()) {
            companyEmail = request.email();
        }
        workspace.setCompanyEmail(companyEmail.toLowerCase());
        workspaceRepository.save(workspace);
        taskTemplateService.ensureDefaultTemplate(workspace.getId());

        Role adminRole = roleRepository.findByCode(RoleCodes.WORKSPACE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("WORKSPACE_ADMIN missing"));
        Membership membership = new Membership();
        membership.setUser(user);
        membership.setRole(adminRole);
        membership.setScopeType(ScopeType.WORKSPACE);
        membership.setWorkspaceId(workspace.getId());
        membershipRepository.save(membership);

        UserDto userDto = userService.get(actorId, user.getId());
        WorkspaceDto workspaceDto = new WorkspaceDto(
                workspace.getId(),
                workspace.getName(),
                workspace.getKey(),
                workspace.getCreatedBy(),
                workspace.getCreatedAt(),
                workspace.getDeletedAt()
        );
        return new ProvisionTenantResponse(userDto, workspaceDto);
    }
}
