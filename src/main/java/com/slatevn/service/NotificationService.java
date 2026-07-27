package com.slatevn.service;

import com.slatevn.domain.Notification;
import com.slatevn.domain.NotificationTypes;
import com.slatevn.dto.NotificationDto;
import com.slatevn.dto.UnreadNotificationCountDto;
import com.slatevn.dto.TaskNotificationContextDto;
import com.slatevn.repository.NotificationRepository;
import com.slatevn.repository.TaskRepository;
import com.slatevn.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TaskRepository taskRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            TaskRepository taskRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public Notification create(UUID userId, String type, UUID referenceId, String title, String body) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        notification.setTitle(title);
        notification.setBody(body);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountDto unreadCount(UUID userId) {
        return new UnreadNotificationCountDto(notificationRepository.countUnreadByUserId(userId));
    }

    @Transactional
    public NotificationDto markRead(UUID userId, UUID notificationId) {
        Notification notification = requireOwnedNotification(userId, notificationId);
        if (!notification.isRead()) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return toDto(notification);
    }

    private Notification requireOwnedNotification(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!userId.equals(notification.getUserId())) {
            throw new NotFoundException("Notification not found");
        }
        return notification;
    }

    private NotificationDto toDto(Notification notification) {
        TaskNotificationContextDto task = null;
        if (NotificationTypes.isTaskType(notification.getType())) {
            task = taskRepository.findById(notification.getReferenceId())
                    .map(t -> new TaskNotificationContextDto(t.getId(), t.getBoardId(), t.getTitle()))
                    .orElse(null);
        }
        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getReferenceId(),
                notification.getTitle(),
                notification.getBody(),
                notification.isRead(),
                notification.getCreatedAt(),
                task
        );
    }
}
