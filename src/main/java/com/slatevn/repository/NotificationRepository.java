package com.slatevn.repository;

import com.slatevn.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.readAt IS NULL")
    long countUnreadByUserId(@Param("userId") UUID userId);

    List<Notification> findByTypeAndReferenceId(String type, UUID referenceId);
}
