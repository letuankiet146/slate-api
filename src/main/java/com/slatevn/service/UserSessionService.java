package com.slatevn.service;

import com.slatevn.domain.RoleCodes;
import com.slatevn.domain.ScopeType;
import com.slatevn.dto.UserSessionDto;
import com.slatevn.repository.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserSessionService {

    private final AuthorizationService authorizationService;
    private final MembershipRepository membershipRepository;

    public UserSessionService(
            AuthorizationService authorizationService,
            MembershipRepository membershipRepository
    ) {
        this.authorizationService = authorizationService;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public UserSessionDto resolveSession(UUID userId) {
        UserSessionDto base = resolveBaseSession(userId);
        String landingPath = authorizationService.hasAssignedTasks(userId)
                ? "/my-tasks"
                : base.landingPath();
        return new UserSessionDto(
                base.primaryRole(),
                landingPath,
                base.hasMembership(),
                base.workspaceAdminWorkspaceIds(),
                authorizationService.canAccessMyTasks(userId),
                authorizationService.canAccessManagedBoards(userId),
                authorizationService.canAccessWorkspaces(userId)
        );
    }

    private UserSessionDto resolveBaseSession(UUID userId) {
        if (authorizationService.isSystemAdmin(userId)) {
            return new UserSessionDto(
                    RoleCodes.SYSTEM_ADMIN,
                    "/",
                    true,
                    authorizationService.workspaceAdminWorkspaceIds(userId),
                    false,
                    false,
                    false
            );
        }

        List<UUID> workspaceAdminIds = authorizationService.workspaceAdminWorkspaceIds(userId);
        if (!workspaceAdminIds.isEmpty()) {
            return new UserSessionDto(
                    RoleCodes.WORKSPACE_ADMIN,
                    landingForWorkspaces(workspaceAdminIds),
                    true,
                    workspaceAdminIds,
                    false,
                    false,
                    false
            );
        }

        if (authorizationService.hasAnyBoardAdminRole(userId)) {
            List<UUID> boardIds = authorizationService.visibleBoardIds(userId).stream()
                    .filter(boardId -> authorizationService.canManageBoard(userId, boardId))
                    .toList();
            if (boardIds.size() == 1) {
                return new UserSessionDto(
                        RoleCodes.BOARD_ADMIN,
                        "/boards/" + boardIds.getFirst(),
                        true,
                        List.of(),
                        false,
                        false,
                        false
                );
            }
            return new UserSessionDto(RoleCodes.BOARD_ADMIN, "/my-boards", true, List.of(), false, false, false);
        }

        if (authorizationService.isBoardScopedMemberOrViewer(userId)) {
            boolean viewerOnly = authorizationService.hasWorkspaceOrBoardViewerRole(userId)
                    && !authorizationService.hasWorkspaceOrBoardMemberRole(userId);
            String role = viewerOnly ? RoleCodes.BOARD_VIEWER : RoleCodes.BOARD_MEMBER;
            return new UserSessionDto(role, "/", true, List.of(), false, false, false);
        }

        boolean hasMembership = membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getScopeType() != ScopeType.SYSTEM);
        return new UserSessionDto("NONE", "/", hasMembership, List.of(), false, false, false);
    }

    private static String landingForWorkspaces(List<UUID> workspaceAdminIds) {
        if (workspaceAdminIds.size() == 1) {
            return "/workspaces/" + workspaceAdminIds.getFirst();
        }
        return "/";
    }
}
