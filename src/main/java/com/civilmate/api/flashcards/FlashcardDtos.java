package com.civilmate.api.flashcards;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class FlashcardDtos {
    private FlashcardDtos() { }
    public record Category(UUID id, String name, String slug, String description) { }
    public record Card(UUID id, UUID categoryId, String category, String categorySlug, String question, String shortAnswer,
            String detailedExplanation, String topic, String difficulty, String relatedFormula, String relatedLaboratoryTest,
            String sourceReference, String progressStatus, boolean bookmarked, int reviewCount, Instant lastReviewedAt, String contentStatus) { }
    public record Page(List<Card> content, int page, int size, long totalElements, int totalPages) { }
    public record CategoryWrite(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=140) String slug, @Size(max=500) String description) { }
    public record CardWrite(@NotNull UUID categoryId, @NotBlank String question, @NotBlank String shortAnswer,
            @NotBlank String detailedExplanation, @NotBlank @Size(max=160) String topic, @NotBlank String difficulty,
            @Size(max=300) String relatedFormula, @Size(max=300) String relatedLaboratoryTest,
            @NotBlank @Size(max=500) String sourceReference, @NotBlank String status) { }
    public record ProgressWrite(@NotBlank String status, Boolean bookmarked, Boolean reviewed) { }
    public record CategoryProgress(String category, long studied, long known, long revision, long total) { }
    public record Progress(long studied, long known, long revision, long bookmarked, List<Card> recent, List<CategoryProgress> categories) { }
}
