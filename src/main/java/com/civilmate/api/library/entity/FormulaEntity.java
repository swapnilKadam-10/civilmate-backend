package com.civilmate.api.library.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
@Table(name = "formulas")
public class FormulaEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id", nullable = false) private CategoryEntity category;
    @Column(nullable = false, length = 180) private String title;
    @Column(nullable = false, unique = true, length = 200) private String slug;
    @Column(nullable = false, length = 140) private String topic;
    @Column(nullable = false, columnDefinition = "text") private String expression;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "variable_definitions", nullable = false, columnDefinition = "jsonb") private List<Map<String, String>> variableDefinitions;
    @Column(nullable = false, columnDefinition = "text") private String explanation;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "example_calculation", columnDefinition = "jsonb") private Map<String, Object> exampleCalculation;
    @Column(name = "source_reference", nullable = false, length = 500) private String sourceReference;
    @Column(nullable = false, length = 20) private String difficulty;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "related_formula_ids", nullable = false, columnDefinition = "jsonb") private List<String> relatedFormulaIds;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContentStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected FormulaEntity() { }
    public FormulaEntity(CategoryEntity category, String title, String slug, String topic, String expression,
            List<Map<String, String>> variableDefinitions, String explanation, Map<String, Object> exampleCalculation,
            String sourceReference, String difficulty, List<String> relatedFormulaIds, ContentStatus status, Instant now) {
        this.id = UUID.randomUUID(); this.category = category; this.title = title; this.slug = slug; this.topic = topic;
        this.expression = expression; this.variableDefinitions = variableDefinitions; this.explanation = explanation;
        this.exampleCalculation = exampleCalculation; this.sourceReference = sourceReference; this.difficulty = difficulty;
        this.relatedFormulaIds = relatedFormulaIds; this.status = status; this.createdAt = now; this.updatedAt = now;
    }
    public void update(CategoryEntity category, String title, String slug, String topic, String expression,
            List<Map<String, String>> variableDefinitions, String explanation, Map<String, Object> exampleCalculation,
            String sourceReference, String difficulty, List<String> relatedFormulaIds, ContentStatus status, Instant now) {
        this.category = category; this.title = title; this.slug = slug; this.topic = topic; this.expression = expression;
        this.variableDefinitions = variableDefinitions; this.explanation = explanation; this.exampleCalculation = exampleCalculation;
        this.sourceReference = sourceReference; this.difficulty = difficulty; this.relatedFormulaIds = relatedFormulaIds;
        this.status = status; this.updatedAt = now;
    }
    public void archive(Instant now) { this.status = ContentStatus.ARCHIVED; this.updatedAt = now; }
    public UUID getId() { return id; }
    public CategoryEntity getCategory() { return category; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getTopic() { return topic; }
    public String getExpression() { return expression; }
    public List<Map<String, String>> getVariableDefinitions() { return variableDefinitions; }
    public String getExplanation() { return explanation; }
    public Map<String, Object> getExampleCalculation() { return exampleCalculation; }
    public String getSourceReference() { return sourceReference; }
    public String getDifficulty() { return difficulty; }
    public List<String> getRelatedFormulaIds() { return relatedFormulaIds; }
    public ContentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
