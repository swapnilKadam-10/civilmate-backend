package com.civilmate.api.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public final class AdminDtos {
    private AdminDtos() { }
    public record UserRow(UUID id,String fullName,String email,String role,boolean active,Instant createdAt,Instant lastLoginAt) { }
    public record UserPage(List<UserRow> content,int page,int size,long totalElements,int totalPages) { }
    public record UserUpdate(@NotBlank String role,@NotNull Boolean active) { }
    public record PopularRow(String label,long count) { }
    public record Analytics(long registeredUsers,long activeUsers30d,long quizAttempts,double averageAccuracy,
            long flashcardReviews,long learnersStudyingCards,List<PopularRow> popularSubjects,List<PopularRow> popularQuizzes) { }
}
