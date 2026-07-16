package com.slatevn.repository;

import com.slatevn.domain.FieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, UUID> {

    List<FieldDefinition> findByTemplateIdOrderByPositionAsc(UUID templateId);

    List<FieldDefinition> findByBoardIdAndTaskIdIsNotNull(UUID boardId);

    void deleteByTemplateId(UUID templateId);

    @Query("""
            SELECT f FROM FieldDefinition f
            WHERE f.taskId = :taskId
               OR (f.taskId IS NULL AND :templateId IS NOT NULL AND f.templateId = :templateId)
            ORDER BY f.position ASC
            """)
    List<FieldDefinition> findApplicableToTask(
            @Param("taskId") UUID taskId,
            @Param("templateId") UUID templateId
    );
}
