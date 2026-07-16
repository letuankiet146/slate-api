package com.slatevn.repository;

import com.slatevn.domain.TaskFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskFieldValueRepository extends JpaRepository<TaskFieldValue, UUID> {
    List<TaskFieldValue> findByTaskId(UUID taskId);
    List<TaskFieldValue> findByTaskIdIn(List<UUID> taskIds);
    Optional<TaskFieldValue> findByTaskIdAndFieldDefinitionId(UUID taskId, UUID fieldDefinitionId);

    void deleteByTaskId(UUID taskId);
}
