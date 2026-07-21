package com.laconic.scheduler.scheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Defines <strong>WHAT</strong> to execute.
 * Timing lives on {@link Schedule}; this entity never carries next-fire logic.
 */
@Entity
@Table(name = "SCHEDULER_ACTION")
public class Action {

    @Id
    @Column(name = "ID", length = 36, nullable = false, updatable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACTION_TYPE", length = 32, nullable = false)
    private ActionType type;

    @Lob
    @Column(name = "PAYLOAD_JSON", nullable = false)
    private String payloadJson;

    @Lob
    @Column(name = "METADATA_JSON")
    private String metadataJson;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    protected Action() {
        // JPA
    }

    public Action(ActionType type, String payloadJson, String metadataJson) {
        this.id = UUID.randomUUID().toString();
        this.type = Objects.requireNonNull(type, "type");
        this.payloadJson = Objects.requireNonNull(payloadJson, "payloadJson");
        this.metadataJson = metadataJson;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public ActionType getType() {
        return type;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void replacePayload(String payloadJson, String metadataJson) {
        this.payloadJson = Objects.requireNonNull(payloadJson, "payloadJson");
        this.metadataJson = metadataJson;
    }
}
