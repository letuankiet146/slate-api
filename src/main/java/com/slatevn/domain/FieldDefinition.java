package com.slatevn.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "field_definitions")
public class FieldDefinition {

    @Id
    private UUID id;

    @Column(name = "board_id")
    private UUID boardId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false)
    private FieldType fieldType;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private boolean editable = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldVisibility visibility = FieldVisibility.INTERNAL;

    @Column(nullable = false)
    private int position;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "field_required_column_names",
            joinColumns = @JoinColumn(name = "field_definition_id")
    )
    @Column(name = "column_name", nullable = false)
    private Set<String> requiredColumnNames = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBoardId() {
        return boardId;
    }

    public void setBoardId(UUID boardId) {
        this.boardId = boardId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public FieldType getFieldType() {
        return fieldType;
    }

    public void setFieldType(FieldType fieldType) {
        this.fieldType = fieldType;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public FieldVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(FieldVisibility visibility) {
        this.visibility = visibility;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Set<String> getRequiredColumnNames() {
        return requiredColumnNames;
    }

    public void setRequiredColumnNames(Set<String> requiredColumnNames) {
        this.requiredColumnNames = requiredColumnNames != null ? requiredColumnNames : new HashSet<>();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRequiredForColumnName(String columnName) {
        if (!required) {
            return false;
        }
        if (requiredColumnNames == null || requiredColumnNames.isEmpty()) {
            return true;
        }
        return requiredColumnNames.contains(columnName);
    }
}
