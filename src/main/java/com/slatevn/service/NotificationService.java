package com.slatevn.service;

import com.slatevn.domain.Notification;
import com.slatevn.domain.NotificationTypes;
import com.slatevn.domain.WorkspaceJoinRequest;
import com.slatevn.dto.NotificationDto;
import com.slatevn.dto.UnreadNotificationCountDto;
import com.slatevn.dto.WorkspaceJoinRequestDto;
import com.slatevn.repository.NotificationRepository;
import com.slatevn.repository.WorkspaceJoinRequestRepository;
import com.slatevn.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final WorkspaceJoinRequestRepository joinRequestRepository;
    private final WorkspaceJoinRequestService joinRequestService;

    public NotificationService(
            NotificationRepository notificationRepository,
            WorkspaceJoinRequestRepository joinRequestRepository,
            WorkspaceJoinRequestService joinRequestService
    ) {
        this.notificationRepository = notificationRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.joinRequestService = joinRequestService;
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
        WorkspaceJoinRequestDto joinRequest = null;
        if (NotificationTypes.WORKSPACE_JOIN_REQUEST.equals(notification.getType())) {
            joinRequest = joinRequestRepository.findById(notification.getReferenceId())
                    .map(joinRequestService::toDto)
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
                joinRequest
        );
    }
}
