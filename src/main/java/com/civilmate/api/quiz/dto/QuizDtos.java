package com.civilmate.api.quiz.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.civilmate.api.library.dto.LibraryDtos.PageView;

public final class QuizDtos {
    private QuizDtos() { }

    public record QuizSummary(UUID id, String title, String examType, String subject, String topic, String difficulty,
            int durationMinutes, int questionCount, BigDecimal totalMarks, String status) { }
    public record OptionView(UUID id, String label, String text, Boolean correct) { }
    public record StartRequest(UUID quizId, String examType, String subject, String topic, String difficulty,
            @Min(1) @Max(100) Integer questionCount, @Min(1) @Max(240) Integer durationMinutes) { }
    public record AnswerRequest(@NotNull List<UUID> selectedOptionIds, Boolean markedForReview) { }
    public record AttemptQuestion(UUID id, int number, String text, String type, String topic, BigDecimal marks,
            List<OptionView> options, List<UUID> selectedOptionIds, boolean visited, boolean markedForReview,
            Boolean correct, List<UUID> correctOptionIds, BigDecimal awardedMarks, String explanation, String sourceReference) { }
    public record TopicResult(String topic, int total, int correct, BigDecimal score, BigDecimal possibleMarks, BigDecimal accuracy) { }
    public record AttemptView(UUID id, UUID quizId, String title, String examType, String subject, String status,
            int totalQuestions, BigDecimal totalMarks, BigDecimal score, Integer correctCount, Integer incorrectCount,
            Integer unansweredCount, BigDecimal accuracy, Instant startedAt, Instant expiresAt, Instant submittedAt,
            long timeTakenSeconds, List<AttemptQuestion> questions, List<TopicResult> topicPerformance) { }
    public record AttemptHistory(UUID id, String title, String examType, String subject, String status, int totalQuestions,
            BigDecimal score, Integer correctCount, Integer incorrectCount, Integer unansweredCount, BigDecimal accuracy,
            Instant startedAt, Instant submittedAt) { }
    public record HistoryPage(PageView<AttemptHistory> page) { }

    public record QuizWriteRequest(@NotBlank @Size(max = 180) String title, @NotBlank String examType,
            @NotBlank @Size(max = 140) String subject, @Size(max = 160) String topic, String difficulty,
            @Min(1) @Max(240) int durationMinutes, @NotBlank String status) { }
    public record OptionWriteRequest(@NotBlank @Size(max = 4) String label, @NotBlank String text, boolean correct) { }
    public record QuestionWriteRequest(@NotNull UUID quizId, @NotBlank String text, @NotBlank String type,
            @NotBlank @Size(max = 160) String topic, @NotBlank String difficulty, @NotNull @DecimalMin("0.01") BigDecimal marks,
            @NotNull @DecimalMin("0.00") BigDecimal negativeMarks, @NotBlank String explanation,
            @NotBlank @Size(max = 500) String sourceReference, @NotBlank String status,
            @NotEmpty @Size(min = 2, max = 8) List<@Valid OptionWriteRequest> options) { }
    public record QuestionImportRequest(@NotEmpty @Size(max=200) List<@Valid QuestionWriteRequest> questions) { }
    public record ImportValidation(int questionCount, String message) { }
    public record AdminOption(UUID id, String label, String text, boolean correct, int order) { }
    public record AdminQuestion(UUID id, UUID quizId, String text, String type, String topic, String difficulty,
            BigDecimal marks, BigDecimal negativeMarks, String explanation, String sourceReference, String status, List<AdminOption> options) { }
}
