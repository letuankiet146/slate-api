package com.slatevn.repository;

import com.slatevn.domain.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    Optional<Workspace> findByKeyIgnoreCase(String key);

    boolean existsByKeyIgnoreCase(String key);

    List<Workspace> findByDeletedAtIsNullOrderByNameAsc();

    List<Workspace> findByDeletedAtIsNotNullOrderByDeletedAtDesc();

    List<Workspace> findByCreatedBy(UUID createdBy);

    List<Workspace> findByDeletedBy(UUID deletedBy);
}
