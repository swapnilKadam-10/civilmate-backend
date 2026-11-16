package com.civilmate.api.library.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bookmarks")
public class BookmarkEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "resource_type", nullable = false, length = 20) private String resourceType;
    @Column(name = "resource_id", nullable = false) private UUID resourceId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected BookmarkEntity() { }
    public BookmarkEntity(UUID userId, String resourceType, UUID resourceId, Instant createdAt) {
        this.id = UUID.randomUUID(); this.userId = userId; this.resourceType = resourceType; this.resourceId = resourceId; this.createdAt = createdAt;
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public Instant getCreatedAt() { return createdAt; }
}
