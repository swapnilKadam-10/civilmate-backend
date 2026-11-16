package com.civilmate.api.library.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "code_references")
public class CodeReferenceEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id", nullable = false) private CategoryEntity category;
    @Column(name = "code_number", nullable = false, length = 80) private String codeNumber;
    @Column(nullable = false, length = 220) private String title;
    @Column(nullable = false, length = 140) private String subject;
    @Column(nullable = false, length = 160) private String topic;
    @Column(length = 120) private String edition;
    @Column(name = "publication_year") private Integer publicationYear;
    @Column(name = "clause_identifier", length = 100) private String clauseIdentifier;
    @Column(nullable = false, columnDefinition = "text") private String summary;
    @Column(name = "permitted_excerpt", columnDefinition = "text") private String permittedExcerpt;
    @Column(name = "source_information", nullable = false, length = 500) private String sourceInformation;
    @Column(name = "source_url", length = 1000) private String sourceUrl;
    @Column(name = "verification_level", nullable = false, length = 30) private String verificationLevel;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "related_reference_ids", nullable = false, columnDefinition = "jsonb") private List<String> relatedReferenceIds;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContentStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CodeReferenceEntity() { }
    public CodeReferenceEntity(CategoryEntity category, String codeNumber, String title, String subject, String topic,
            String edition, Integer publicationYear, String clauseIdentifier, String summary, String permittedExcerpt,
            String sourceInformation, String sourceUrl, String verificationLevel, List<String> relatedReferenceIds,
            ContentStatus status, Instant now) {
        this.id = UUID.randomUUID(); this.category = category; this.codeNumber = codeNumber; this.title = title; this.subject = subject;
        this.topic = topic; this.edition = edition; this.publicationYear = publicationYear; this.clauseIdentifier = clauseIdentifier;
        this.summary = summary; this.permittedExcerpt = permittedExcerpt; this.sourceInformation = sourceInformation; this.sourceUrl = sourceUrl;
        this.verificationLevel = verificationLevel; this.relatedReferenceIds = relatedReferenceIds; this.status = status; this.createdAt = now; this.updatedAt = now;
    }
    public void update(CategoryEntity category, String codeNumber, String title, String subject, String topic, String edition,
            Integer publicationYear, String clauseIdentifier, String summary, String permittedExcerpt, String sourceInformation,
            String sourceUrl, String verificationLevel, List<String> relatedReferenceIds, ContentStatus status, Instant now) {
        this.category = category; this.codeNumber = codeNumber; this.title = title; this.subject = subject; this.topic = topic;
        this.edition = edition; this.publicationYear = publicationYear; this.clauseIdentifier = clauseIdentifier;
        this.summary = summary; this.permittedExcerpt = permittedExcerpt; this.sourceInformation = sourceInformation; this.sourceUrl = sourceUrl;
        this.verificationLevel = verificationLevel; this.relatedReferenceIds = relatedReferenceIds; this.status = status; this.updatedAt = now;
    }
    public void archive(Instant now) { this.status = ContentStatus.ARCHIVED; this.updatedAt = now; }
    public UUID getId() { return id; }
    public CategoryEntity getCategory() { return category; }
    public String getCodeNumber() { return codeNumber; }
    public String getTitle() { return title; }
    public String getSubject() { return subject; }
    public String getTopic() { return topic; }
    public String getEdition() { return edition; }
    public Integer getPublicationYear() { return publicationYear; }
    public String getClauseIdentifier() { return clauseIdentifier; }
    public String getSummary() { return summary; }
    public String getPermittedExcerpt() { return permittedExcerpt; }
    public String getSourceInformation() { return sourceInformation; }
    public String getSourceUrl() { return sourceUrl; }
    public String getVerificationLevel() { return verificationLevel; }
    public List<String> getRelatedReferenceIds() { return relatedReferenceIds; }
    public ContentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
