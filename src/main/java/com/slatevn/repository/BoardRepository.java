package com.slatevn.repository;

import com.slatevn.domain.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BoardRepository extends JpaRepository<Board, UUID> {
    List<Board> findByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(UUID workspaceId);

    List<Board> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    List<Board> findByDeletedAtIsNotNullOrderByDeletedAtDesc();

    List<Board> findByWorkspaceIdInAndDeletedAtIsNotNullOrderByDeletedAtDesc(Collection<UUID> workspaceIds);

    List<Board> findByCreatedBy(UUID createdBy);

    List<Board> findByDeletedBy(UUID deletedBy);
}
