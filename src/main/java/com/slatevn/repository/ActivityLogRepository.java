package com.slatevn.repository;

import com.slatevn.domain.ActivityLog;
import com.slatevn.domain.ActivityScopeLevel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    @Query("""
            SELECT a FROM ActivityLog a
            WHERE a.workspaceId = :workspaceId
              AND a.scopeLevel IN :scopeLevels
              AND (
                a.scopeLevel = com.slatevn.domain.ActivityScopeLevel.WORKSPACE
                OR a.boardId IS NULL
                OR a.boardId IN :accessibleBoardIds
              )
            ORDER BY a.createdAt DESC
            """)
    List<ActivityLog> findWorkspaceHistory(
            @Param("workspaceId") UUID workspaceId,
            @Param("scopeLevels") Collection<ActivityScopeLevel> scopeLevels,
            @Param("accessibleBoardIds") Collection<UUID> accessibleBoardIds,
            Pageable pageable
    );

    @Query("""
            SELECT a FROM ActivityLog a
            WHERE a.boardId = :boardId
              AND a.scopeLevel IN :scopeLevels
            ORDER BY a.createdAt DESC
            """)
    List<ActivityLog> findBoardHistory(
            @Param("boardId") UUID boardId,
            @Param("scopeLevels") Collection<ActivityScopeLevel> scopeLevels,
            Pageable pageable
    );

    @Query("""
            SELECT a FROM ActivityLog a
            WHERE a.taskId = :taskId
              AND a.scopeLevel = com.slatevn.domain.ActivityScopeLevel.TASK
            ORDER BY a.createdAt DESC
            """)
    List<ActivityLog> findTaskHistory(
            @Param("taskId") UUID taskId,
            Pageable pageable
    );
}
