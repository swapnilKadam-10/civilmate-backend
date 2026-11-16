package com.civilmate.api.library.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.civilmate.api.library.entity.CategoryEntity;
import com.civilmate.api.library.entity.CodeReferenceEntity;
import com.civilmate.api.library.entity.FormulaEntity;

public final class LibraryDtos {
    private LibraryDtos() { }

    public record CategoryView(UUID id, String name, String slug, String subject, String description, String resourceType) {
        public static CategoryView from(CategoryEntity category) {
            return new CategoryView(category.getId(), category.getName(), category.getSlug(), category.getSubject(), category.getDescription(), category.getResourceType());
        }
    }
    public record FormulaCard(UUID id, String title, String slug, String category, String categorySlug, String topic, String expression, String difficulty) { }
    public record FormulaView(UUID id, String title, String slug, String category, String categorySlug, String subject, String topic,
            String expression, List<Map<String, String>> variableDefinitions, String explanation, Map<String, Object> exampleCalculation,
            String sourceReference, String difficulty, List<String> relatedFormulaIds, Instant updatedAt, UUID categoryId, String status) {
        public static FormulaView from(FormulaEntity formula) {
            return new FormulaView(formula.getId(), formula.getTitle(), formula.getSlug(), formula.getCategory().getName(), formula.getCategory().getSlug(),
                    formula.getCategory().getSubject(), formula.getTopic(), formula.getExpression(), formula.getVariableDefinitions(), formula.getExplanation(),
                    formula.getExampleCalculation(), formula.getSourceReference(), formula.getDifficulty(), formula.getRelatedFormulaIds(), formula.getUpdatedAt(), formula.getCategory().getId(), formula.getStatus().name());
        }
    }
    public record CodeCard(UUID id, String codeNumber, String title, String category, String categorySlug, String subject, String topic,
            String edition, Integer publicationYear, String verificationLevel) { }
    public record CodeView(UUID id, String codeNumber, String title, String category, String categorySlug, String subject, String topic,
            String edition, Integer publicationYear, String clauseIdentifier, String summary, String permittedExcerpt,
            String sourceInformation, String sourceUrl, String verificationLevel, List<String> relatedReferenceIds, Instant updatedAt, UUID categoryId, String status) {
        public static CodeView from(CodeReferenceEntity code) {
            return new CodeView(code.getId(), code.getCodeNumber(), code.getTitle(), code.getCategory().getName(), code.getCategory().getSlug(),
                    code.getSubject(), code.getTopic(), code.getEdition(), code.getPublicationYear(), code.getClauseIdentifier(), code.getSummary(),
                    code.getPermittedExcerpt(), code.getSourceInformation(), code.getSourceUrl(), code.getVerificationLevel(), code.getRelatedReferenceIds(), code.getUpdatedAt(), code.getCategory().getId(), code.getStatus().name());
        }
    }
    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) { }
    public record BookmarkRequest(@NotBlank String resourceType, @NotNull UUID resourceId) { }
    public record BookmarkView(UUID id, String resourceType, UUID resourceId, String title, String topic, String category, Instant createdAt) { }

    public record CategoryWriteRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 140) String slug,
            @NotBlank @Size(max = 120) String subject, @Size(max = 500) String description, @NotBlank String resourceType) { }
    public record FormulaWriteRequest(@NotNull UUID categoryId, @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 200) String slug, @NotBlank @Size(max = 140) String topic, @NotBlank String expression,
            @NotEmpty List<Map<String, String>> variableDefinitions, @NotBlank String explanation,
            Map<String, Object> exampleCalculation, @NotBlank @Size(max = 500) String sourceReference,
            @NotBlank String difficulty, List<String> relatedFormulaIds, @NotBlank String status) { }
    public record CodeWriteRequest(@NotNull UUID categoryId, @NotBlank @Size(max = 80) String codeNumber,
            @NotBlank @Size(max = 220) String title, @NotBlank @Size(max = 140) String subject,
            @NotBlank @Size(max = 160) String topic, @Size(max = 120) String edition,
            @Min(1900) @Max(2200) Integer publicationYear, @Size(max = 100) String clauseIdentifier,
            @NotBlank String summary, String permittedExcerpt, @NotBlank @Size(max = 500) String sourceInformation,
            @Size(max = 1000) String sourceUrl, @NotBlank String verificationLevel,
            List<String> relatedReferenceIds, @NotBlank String status) { }
}
