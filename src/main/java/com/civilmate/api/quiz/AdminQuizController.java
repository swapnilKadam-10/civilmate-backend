package com.civilmate.api.quiz;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.civilmate.api.auth.dto.ApiEnvelope;
import com.civilmate.api.quiz.dto.QuizDtos.QuestionWriteRequest;
import com.civilmate.api.quiz.dto.QuizDtos.QuestionImportRequest;
import com.civilmate.api.quiz.dto.QuizDtos.QuizWriteRequest;
import com.civilmate.api.quiz.service.QuizService;

@Validated
@RestController
@RequestMapping("/api/v1/admin/quizzes")
public class AdminQuizController {
    private final QuizService quizzes;
    public AdminQuizController(QuizService quizzes) { this.quizzes = quizzes; }

    @GetMapping
    public ApiEnvelope<?> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiEnvelope.success("Quiz configurations retrieved.", quizzes.adminQuizzes(status, page, size));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<?> create(@Valid @RequestBody QuizWriteRequest request) { return ApiEnvelope.success("Quiz configuration created.", quizzes.createQuiz(request)); }

    @PutMapping("/{id}")
    public ApiEnvelope<?> update(@PathVariable UUID id, @Valid @RequestBody QuizWriteRequest request) { return ApiEnvelope.success("Quiz configuration updated.", quizzes.updateQuiz(id, request)); }

    @DeleteMapping("/{id}")
    public ApiEnvelope<Void> archive(@PathVariable UUID id) { quizzes.archiveQuiz(id); return ApiEnvelope.success("Quiz configuration archived.", null); }

    @GetMapping("/{quizId}/questions")
    public ApiEnvelope<?> questions(@PathVariable UUID quizId, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) { return ApiEnvelope.success("Quiz questions retrieved.", quizzes.adminQuestions(quizId, page, size)); }

    @PostMapping("/{quizId}/questions") @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<?> createQuestion(@PathVariable UUID quizId, @Valid @RequestBody QuestionWriteRequest request) {
        if (!quizId.equals(request.quizId())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Request quizId must match the URL.");
        return ApiEnvelope.success("Question created.", quizzes.createQuestion(request));
    }

    @PostMapping("/{quizId}/questions/import/validate")
    public ApiEnvelope<?> validateImport(@PathVariable UUID quizId,@Valid @RequestBody QuestionImportRequest request) {
        return ApiEnvelope.success("Quiz import validated.",quizzes.validateQuestionImport(quizId,request));
    }

    @PostMapping("/{quizId}/questions/import") @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<?> importQuestions(@PathVariable UUID quizId,@Valid @RequestBody QuestionImportRequest request) {
        return ApiEnvelope.success("Quiz questions imported.",quizzes.importQuestions(quizId,request));
    }

    @PutMapping("/questions/{questionId}")
    public ApiEnvelope<?> updateQuestion(@PathVariable UUID questionId, @Valid @RequestBody QuestionWriteRequest request) { return ApiEnvelope.success("Question updated.", quizzes.updateQuestion(questionId, request)); }

    @DeleteMapping("/questions/{questionId}")
    public ApiEnvelope<Void> archiveQuestion(@PathVariable UUID questionId) { quizzes.archiveQuestion(questionId); return ApiEnvelope.success("Question archived.", null); }
}
