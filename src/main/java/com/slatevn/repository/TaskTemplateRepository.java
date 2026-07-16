package com.slatevn.repository;

import com.slatevn.domain.TaskTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, UUID> {

    List<TaskTemplate> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    Optional<TaskTemplate> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<TaskTemplate> findByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    boolean existsByWorkspaceIdAndNameIgnoreCaseAndIdNot(UUID workspaceId, String name, UUID id);

    @Query("""
            SELECT t FROM TaskTemplate t
            JOIN t.visibleBoardIds b
            WHERE b = :boardId
            ORDER BY t.name ASC
            """)
    List<TaskTemplate> findVisibleOnBoard(@Param("boardId") UUID boardId);
}