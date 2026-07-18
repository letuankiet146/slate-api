package com.slatevn.repository;

import com.slatevn.domain.Membership;
import com.slatevn.domain.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    List<Membership> findByUserId(UUID userId);

    List<Membership> findByWorkspaceId(UUID workspaceId);

    List<Membership> findByBoardId(UUID boardId);

    @Query("""
            SELECT m FROM Membership m
            WHERE m.user.id = :userId
              AND (
                m.scopeType = com.slatevn.domain.ScopeType.SYSTEM
                OR (m.scopeType = com.slatevn.domain.ScopeType.WORKSPACE AND m.workspaceId = :workspaceId)
                OR (m.scopeType = com.slatevn.domain.ScopeType.BOARD AND m.boardId = :boardId)
                OR (m.scopeType = com.slatevn.domain.ScopeType.BOARD AND m.workspaceId = :workspaceId)
              )
            """)
    List<Membership> findRelevantForBoard(
            @Param("userId") UUID userId,
            @Param("workspaceId") UUID workspaceId,
            @Param("boardId") UUID boardId
    );

    List<Membership> findByUserIdAndScopeType(UUID userId, ScopeType scopeType);

    long countByScopeTypeAndRole_Code(ScopeType scopeType, String roleCode);

    boolean existsByRole_Id(UUID roleId);

    @Query("""
            SELECT DISTINCT m.user.id FROM Membership m
            WHERE m.scopeType = com.slatevn.domain.ScopeType.SYSTEM
              AND m.role.code = :roleCode
            """)
    List<UUID> findUserIdsBySystemRoleCode(@Param("roleCode") String roleCode);
}
