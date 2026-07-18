package com.slatevn.repository;

import com.slatevn.domain.JoinRequestStatus;
import com.slatevn.domain.WorkspaceJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceJoinRequestRepository extends JpaRepository<WorkspaceJoinRequest, UUID> {
    Optional<WorkspaceJoinRequest> findByUserIdAndWorkspaceIdAndStatus(
            UUID userId, UUID workspaceId, JoinRequestStatus status);

    List<WorkspaceJoinRequest> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(
            UUID workspaceId, JoinRequestStatus status);
}
