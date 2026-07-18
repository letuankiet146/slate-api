package com.slatevn.service;

import com.slatevn.domain.JoinRequestStatus;
import com.slatevn.domain.Membership;
import com.slatevn.domain.NotificationTypes;
import com.slatevn.domain.Role;
import com.slatevn.domain.RoleCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.User;
import com.slatevn.domain.Workspace;
import com.slatevn.domain.WorkspaceJoinRequest;
import com.slatevn.dto.RegisterRequest;
import com.slatevn.dto.RegisterResponse;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.RoleRepository;
import com.slatevn.repository.UserRepository;
import com.slatevn.repository.WorkspaceJoinRequestRepository;
import com.slatevn.repository.WorkspaceRepository;
import com.slatevn.web.BadRequestException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceJoinRequestRepository joinRequestRepository;
    private final MembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceJoinRequestRepository joinRequestRepository,
            MembershipRepository membershipRepository,
            RoleRepository roleRepository,
            NotificationService notificationService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("Email already exists");
        }

        User user = new User();
        user.setEmail(request.email().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setLocale("vi");
        user.setEnabled(true);
        userRepository.save(user);

        boolean joinRequestSent = false;
        if (request.companyEmail() != null && !request.companyEmail().isBlank()) {
            joinRequestSent = maybeCreateJoinRequest(user, request.companyEmail().trim());
        }

        return new RegisterResponse("Registration successful", joinRequestSent);
    }

    private boolean maybeCreateJoinRequest(User user, String companyEmail) {
        Optional<Workspace> workspace = findWorkspaceByCompanyEmail(companyEmail);
        if (workspace.isEmpty()) {
            return false;
        }

        Workspace target = workspace.get();
        if (isAlreadyMember(user.getId(), target.getId())) {
            return false;
        }

        if (joinRequestRepository.findByUserIdAndWorkspaceIdAndStatus(
                user.getId(), target.getId(), JoinRequestStatus.PENDING).isPresent()) {
            return false;
        }

        WorkspaceJoinRequest joinRequest = new WorkspaceJoinRequest();
        joinRequest.setUser(user);
        joinRequest.setWorkspace(target);
        joinRequest.setCompanyEmail(companyEmail.toLowerCase(Locale.ROOT));
        joinRequest.setStatus(JoinRequestStatus.PENDING);
        joinRequest.setRoleCode(RoleCodes.BOARD_MEMBER);
        joinRequestRepository.save(joinRequest);

        notifyWorkspaceAdmins(joinRequest, user, target);
        return true;
    }

    private void notifyWorkspaceAdmins(WorkspaceJoinRequest joinRequest, User applicant, Workspace workspace) {
        Role adminRole = roleRepository.findByCode(RoleCodes.WORKSPACE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("WORKSPACE_ADMIN missing"));
        List<Membership> admins = membershipRepository.findByWorkspaceId(workspace.getId()).stream()
                .filter(m -> m.getRole().getId().equals(adminRole.getId()))
                .filter(m -> m.getScopeType() == ScopeType.WORKSPACE)
                .toList();

        String title = applicant.getDisplayName() + " wants to join " + workspace.getName();
        String body = applicant.getEmail() + " · " + joinRequest.getCompanyEmail();

        for (Membership adminMembership : admins) {
            notificationService.create(
                    adminMembership.getUser().getId(),
                    NotificationTypes.WORKSPACE_JOIN_REQUEST,
                    joinRequest.getId(),
                    title,
                    body
            );
        }
    }

    private Optional<Workspace> findWorkspaceByCompanyEmail(String companyEmail) {
        String normalized = companyEmail.toLowerCase(Locale.ROOT);
        return workspaceRepository.findByDeletedAtIsNullAndCompanyEmailIsNotNull().stream()
                .filter(w -> matchesCompanyEmail(normalized, w.getCompanyEmail()))
                .findFirst();
    }

    static boolean matchesCompanyEmail(String inputEmail, String workspaceCompanyEmail) {
        if (workspaceCompanyEmail == null || workspaceCompanyEmail.isBlank()) {
            return false;
        }
        String normalizedWorkspace = workspaceCompanyEmail.toLowerCase(Locale.ROOT).trim();
        if (inputEmail.equals(normalizedWorkspace)) {
            return true;
        }
        String inputDomain = emailDomain(inputEmail);
        String workspaceDomain = emailDomain(normalizedWorkspace);
        return inputDomain != null && inputDomain.equals(workspaceDomain);
    }

    private static String emailDomain(String email) {
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return null;
        }
        return email.substring(at + 1);
    }

    private boolean isAlreadyMember(java.util.UUID userId, java.util.UUID workspaceId) {
        return membershipRepository.findByWorkspaceId(workspaceId).stream()
                .anyMatch(m -> userId.equals(m.getUser().getId()));
    }
}
