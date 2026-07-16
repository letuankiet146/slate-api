package com.slatevn.repository;

import com.slatevn.domain.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, UUID> {
    List<BoardColumn> findByBoardIdOrderByPositionAsc(UUID boardId);
    Optional<BoardColumn> findByIdAndBoardId(UUID id, UUID boardId);
}
