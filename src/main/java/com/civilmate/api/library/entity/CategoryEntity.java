package com.civilmate.api.library.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class CategoryEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, unique = true, length = 140) private String slug;
    @Column(nullable = false, length = 120) private String subject;
    @Column(length = 500) private String description;
    @Column(name = "resource_type", nullable = false, length = 30) private String resourceType;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CategoryEntity() { }
    public CategoryEntity(String name, String slug, String subject, String description, String resourceType, Instant now) {
        this.id = UUID.randomUUID(); this.name = name; this.slug = slug; this.subject = subject;
        this.description = description; this.resourceType = resourceType; this.createdAt = now; this.updatedAt = now;
    }
    public void update(String name, String slug, String subject, String description, String resourceType, Instant now) {
        this.name = name; this.slug = slug; this.subject = subject; this.description = description;
        this.resourceType = resourceType; this.updatedAt = now;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public String getResourceType() { return resourceType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
