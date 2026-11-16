package com.civilmate.api.quiz;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatusCode;
import com.civilmate.api.auth.dto.ApiEnvelope;
import com.civilmate.api.quiz.dto.QuizDtos.AnswerRequest;
import com.civilmate.api.quiz.dto.QuizDtos.AttemptView;
import com.civilmate.api.quiz.dto.QuizDtos.StartRequest;
import com.civilmate.api.quiz.service.QuizService;

@Validated
@RestController
@RequestMapping("/api/v1")
public class QuizController {
    private final QuizService quizzes;
    public QuizController(QuizService quizzes) { this.quizzes = quizzes; }

    @GetMapping("/quizzes")
    public ApiEnvelope<?> quizzes(@RequestParam(required = false) String examType, @RequestParam(required = false) String subject,
            @RequestParam(required = false) String difficulty, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiEnvelope.success("Available quizzes retrieved.", quizzes.quizzes(examType, subject, difficulty, page, size));
    }

    @GetMapping("/quizzes/{id}")
    public ApiEnvelope<?> quiz(@PathVariable UUID id) { return ApiEnvelope.success("Quiz retrieved.", quizzes.quiz(id)); }

    @PostMapping("/quiz-attempts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<AttemptView> start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartRequest request) {
        return ApiEnvelope.success("Quiz attempt started.", quizzes.start(userId(jwt), request));
    }

    @GetMapping("/quiz-attempts/history")
    public ApiEnvelope<?> history(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiEnvelope.success("Quiz attempt history retrieved.", quizzes.history(userId(jwt), page, size));
    }

    @GetMapping("/quiz-attempts/{attemptId}")
    public ApiEnvelope<AttemptView> attempt(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return ApiEnvelope.success("Quiz attempt retrieved.", quizzes.attempt(userId(jwt), attemptId));
    }

    @PutMapping("/quiz-attempts/{attemptId}/answers/{questionAttemptId}")
    public ApiEnvelope<AttemptView> answer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
            @PathVariable UUID questionAttemptId, @Valid @RequestBody AnswerRequest request) {
        return ApiEnvelope.success("Answer saved.", quizzes.answer(userId(jwt), attemptId, questionAttemptId, request));
    }

    @PostMapping("/quiz-attempts/{attemptId}/submit")
    public ApiEnvelope<AttemptView> submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return ApiEnvelope.success("Quiz attempt submitted.", quizzes.submit(userId(jwt), attemptId));
    }

    private static UUID userId(Jwt jwt) {
        try { return UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatusCode.valueOf(401), "Invalid access token."); }
    }
}
