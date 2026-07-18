package com.slatevn.service;

import com.slatevn.domain.JoinRequestStatus;
import com.slatevn.domain.Membership;
import com.slatevn.domain.Notification;
import com.slatevn.domain.NotificationTypes;
import com.slatevn.domain.Role;
import com.slatevn.domain.RoleCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.WorkspaceJoinRequest;
import com.slatevn.dto.WorkspaceJoinRequestDto;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.NotificationRepository;
import com.slatevn.repository.RoleRepository;
import com.slatevn.repository.WorkspaceJoinRequestRepository;
import com.slatevn.web.BadRequestException;
import com.slatevn.web.ForbiddenException;
import com.slatevn.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class WorkspaceJoinRequestService {

    private final WorkspaceJoinRequestRepository joinRequestRepository;
    private final MembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final NotificationRepository notificationRepository;
    private final AuthorizationService authorizationService;

    public WorkspaceJoinRequestService(
            WorkspaceJoinRequestRepository joinRequestRepository,
            MembershipRepository membershipRepository,
            RoleRepository roleRepository,
            NotificationRepository notificationRepository,
            AuthorizationService authorizationService
    ) {
        this.joinRequestRepository = joinRequestRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.notificationRepository = notificationRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public WorkspaceJoinRequestDto approve(UUID actorId, UUID requestId) {
        WorkspaceJoinRequest request = requirePendingRequest(requestId);
        requireWorkspaceAdminForRequest(actorId, request);

        if (authorizationService.isSystemAdmin(request.getUser().getId())) {
            throw new BadRequestException("System administrators cannot be added as workspace members");
        }
        if (isAlreadyMember(request.getUser().getId(), request.getWorkspace().getId())) {
            request.setStatus(JoinRequestStatus.APPROVED);
            request.setReviewedBy(actorId);
            request.setReviewedAt(Instant.now());
            markRelatedNotificationsRead(request.getId());
            return toDto(joinRequestRepository.save(request));
        }

        Role role = roleRepository.findByCode(request.getRoleCode())
                .orElseThrow(() -> new BadRequestException("Unknown role: " + request.getRoleCode()));

        Membership membership = new Membership();
        membership.setUser(request.getUser());
        membership.setRole(role);
        membership.setScopeType(ScopeType.WORKSPACE);
        membership.setWorkspaceId(request.getWorkspace().getId());
        membershipRepository.save(membership);

        request.setStatus(JoinRequestStatus.APPROVED);
        request.setReviewedBy(actorId);
        request.setReviewedAt(Instant.now());
        markRelatedNotificationsRead(request.getId());
        return toDto(joinRequestRepository.save(request));
    }

    @Transactional
    public WorkspaceJoinRequestDto reject(UUID actorId, UUID requestId) {
        WorkspaceJoinRequest request = requirePendingRequest(requestId);
        requireWorkspaceAdminForRequest(actorId, request);

        request.setStatus(JoinRequestStatus.REJECTED);
        request.setReviewedBy(actorId);
        request.setReviewedAt(Instant.now());
        markRelatedNotificationsRead(request.getId());
        return toDto(joinRequestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public WorkspaceJoinRequestDto get(UUID actorId, UUID requestId) {
        WorkspaceJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Join request not found"));
        requireWorkspaceAdminForRequest(actorId, request);
        return toDto(request);
    }

    WorkspaceJoinRequestDto toDto(WorkspaceJoinRequest request) {
        return new WorkspaceJoinRequestDto(
                request.getId(),
                request.getUser().getId(),
                request.getUser().getEmail(),
                request.getUser().getDisplayName(),
                request.getWorkspace().getId(),
                request.getWorkspace().getName(),
                request.getCompanyEmail(),
                request.getStatus().name(),
                request.getRoleCode(),
                request.getCreatedAt()
        );
    }

    private WorkspaceJoinRequest requirePendingRequest(UUID requestId) {
        WorkspaceJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Join request not found"));
        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new BadRequestException("Join request is no longer pending");
        }
        return request;
    }

    private void requireWorkspaceAdminForRequest(UUID actorId, WorkspaceJoinRequest request) {
        if (authorizationService.isSystemAdmin(actorId)) {
            throw new ForbiddenException("System administrators cannot manage workspace join requests");
        }
        authorizationService.requireWorkspaceAdmin(actorId, request.getWorkspace().getId());
    }

    private void markRelatedNotificationsRead(UUID requestId) {
        notificationRepository.findByTypeAndReferenceId(
                        NotificationTypes.WORKSPACE_JOIN_REQUEST, requestId)
                .stream()
                .filter(n -> !n.isRead())
                .forEach(n -> {
                    n.setReadAt(Instant.now());
                    notificationRepository.save(n);
                });
    }

    private boolean isAlreadyMember(UUID userId, UUID workspaceId) {
        return membershipRepository.findByWorkspaceId(workspaceId).stream()
                .anyMatch(m -> userId.equals(m.getUser().getId()));
    }
}
