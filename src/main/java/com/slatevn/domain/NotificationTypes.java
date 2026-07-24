package com.slatevn.domain;

public final class NotificationTypes {
    public static final String WORKSPACE_JOIN_REQUEST = "WORKSPACE_JOIN_REQUEST";
    public static final String TASK_ASSIGNED = "TASK_ASSIGNED";
    public static final String TASK_UPDATED = "TASK_UPDATED";
    public static final String TASK_COMMENT = "TASK_COMMENT";
    public static final String TASK_MENTION = "TASK_MENTION";

    private NotificationTypes() {
    }

    public static boolean isTaskType(String type) {
        return TASK_ASSIGNED.equals(type)
                || TASK_UPDATED.equals(type)
                || TASK_COMMENT.equals(type)
                || TASK_MENTION.equals(type);
    }
}
