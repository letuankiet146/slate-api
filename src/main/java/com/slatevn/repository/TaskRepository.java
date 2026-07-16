package com.slatevn.repository;

import com.slatevn.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByBoardIdAndDeletedAtIsNullOrderByPositionAsc(UUID boardId);

    List<Task> findByColumnIdAndDeletedAtIsNullOrderByPositionAsc(UUID columnId);

    List<Task> findByBoardIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(UUID boardId);

    List<Task> findByBoardIdInAndDeletedAtIsNotNullOrderByDeletedAtDesc(Collection<UUID> boardIds);

    boolean existsByTemplateId(UUID templateId);

    long countByTemplateId(UUID templateId);

    List<Task> findByCreatedBy(UUID createdBy);

    List<Task> findByAssigneeId(UUID assigneeId);

    List<Task> findByDeletedBy(UUID deletedBy);
}
