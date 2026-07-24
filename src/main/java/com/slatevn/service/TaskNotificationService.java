package com.slatevn.service;

import com.slatevn.domain.NotificationTypes;
import com.slatevn.domain.Task;
import com.slatevn.domain.User;
import com.slatevn.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaskNotificationService {

    private static final Pattern EMAIL_MENTION = Pattern.compile(
            "@([\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,})",
            Pattern.CASE_INSENSITIVE
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public TaskNotificationService(
            NotificationService notificationService,
            UserRepository userRepository
    ) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    public void notifyAssigned(UUID actorId, Task task, String actorName) {
        UUID assigneeId = task.getAssigneeId();
        if (assigneeId == null || assigneeId.equals(actorId)) {
            return;
        }
        notificationService.create(
                assigneeId,
                NotificationTypes.TASK_ASSIGNED,
                task.getId(),
                "You were assigned to \"" + task.getTitle() + "\"",
                actorName + " assigned you this task"
        );
    }

    public void notifyUpdated(UUID actorId, Task task, String actorName, String changeSummary) {
        notifyStakeholder(actorId, task, actorName, NotificationTypes.TASK_UPDATED,
                "Task \"" + task.getTitle() + "\" was updated",
                actorName + " updated the task" + (changeSummary.isBlank() ? "" : ": " + changeSummary));
    }

    public void notifyMoved(UUID actorId, Task task, String actorName, String moveSummary) {
        notifyStakeholder(actorId, task, actorName, NotificationTypes.TASK_UPDATED,
                "Task \"" + task.getTitle() + "\" was moved",
                actorName + " " + moveSummary);
    }

    public void notifyMentions(
            UUID actorId,
            Task task,
            String actorName,
            String commentBody,
            List<MentionCandidate> mentionCandidates
    ) {
        notifyMentions(actorId, task, actorName, commentBody, mentionCandidates, Set.of());
    }

    public void notifyMentions(
            UUID actorId,
            Task task,
            String actorName,
            String commentBody,
            List<MentionCandidate> mentionCandidates,
            Set<UUID> excludeUserIds
    ) {
        Set<UUID> mentioned = parseMentionedUserIds(commentBody, mentionCandidates);
        String preview = truncate(commentBody, 120);

        for (UUID mentionedId : mentioned) {
            if (mentionedId.equals(actorId) || excludeUserIds.contains(mentionedId)) {
                continue;
            }
            notificationService.create(
                    mentionedId,
                    NotificationTypes.TASK_MENTION,
                    task.getId(),
                    "You were mentioned on \"" + task.getTitle() + "\"",
                    actorName + ": " + preview
            );
        }
    }

    private void notifyStakeholder(UUID actorId, Task task, String actorName, String type, String title, String body) {
        UUID assigneeId = task.getAssigneeId();
        if (assigneeId == null || assigneeId.equals(actorId)) {
            return;
        }
        notificationService.create(assigneeId, type, task.getId(), title, body);
    }

    public Set<UUID> parseMentionedUserIds(String body, List<MentionCandidate> candidates) {
        Set<UUID> mentioned = new LinkedHashSet<>();
        if (body == null || body.isBlank()) {
            return mentioned;
        }

        Matcher emailMatcher = EMAIL_MENTION.matcher(body);
        while (emailMatcher.find()) {
            String email = emailMatcher.group(1).toLowerCase(Locale.ROOT);
            userRepository.findByEmailIgnoreCase(email)
                    .filter(user -> !user.isDeleted())
                    .ifPresent(user -> mentioned.add(user.getId()));
        }

        String lowerBody = body.toLowerCase(Locale.ROOT);
        List<MentionCandidate> sorted = candidates.stream()
                .sorted((a, b) -> Integer.compare(b.displayName().length(), a.displayName().length()))
                .toList();
        for (MentionCandidate candidate : sorted) {
            String needle = "@" + candidate.displayName().toLowerCase(Locale.ROOT);
            if (lowerBody.contains(needle)) {
                mentioned.add(candidate.userId());
            }
        }
        return mentioned;
    }

    public String actorName(UUID actorId) {
        return userRepository.findById(actorId)
                .map(User::getDisplayName)
                .orElse("Someone");
    }

    private static String truncate(String value, int maxLength) {
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength - 1) + "…";
    }

    public record MentionCandidate(UUID userId, String displayName, String email) {
    }
}
