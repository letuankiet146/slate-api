package com.slatevn.web;

import com.slatevn.dto.NotificationDto;
import com.slatevn.dto.UnreadNotificationCountDto;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationDto> list() {
        return notificationService.list(SecurityUtils.currentUser().getId());
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountDto unreadCount() {
        return notificationService.unreadCount(SecurityUtils.currentUser().getId());
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable UUID id) {
        return notificationService.markRead(SecurityUtils.currentUser().getId(), id);
    }
}
