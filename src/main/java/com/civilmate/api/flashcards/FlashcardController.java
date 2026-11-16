package com.civilmate.api.flashcards;

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
import com.civilmate.api.flashcards.FlashcardDtos.*;

@Validated
@RestController
@RequestMapping("/api/v1")
public class FlashcardController {
    private final FlashcardService service;
    public FlashcardController(FlashcardService service) { this.service=service; }

    @GetMapping("/flashcard-categories") public ApiEnvelope<?> categories() { return ApiEnvelope.success("Flashcard categories retrieved.",service.categories()); }
    @GetMapping("/flashcards") public ApiEnvelope<Page> cards(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String category,
            @RequestParam(required=false) String difficulty,@RequestParam(required=false) String status,@RequestParam(defaultValue="false") boolean revision,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="24") @Min(1) @Max(100) int size) {
        return ApiEnvelope.success("Flashcards retrieved.",service.cards(user(jwt),category,difficulty,status,revision,page,size));
    }
    @GetMapping("/flashcards/progress") public ApiEnvelope<Progress> progress(@AuthenticationPrincipal Jwt jwt) { return ApiEnvelope.success("Flashcard learning progress retrieved.",service.summary(user(jwt))); }
    @GetMapping("/flashcards/{id}") public ApiEnvelope<Card> card(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) { return ApiEnvelope.success("Flashcard retrieved.",service.card(user(jwt),id)); }
    @PostMapping("/flashcards/{id}/progress") public ApiEnvelope<Card> updateProgress(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody ProgressWrite request) { return ApiEnvelope.success("Flashcard progress saved.",service.progress(user(jwt),id,request)); }
    private static UUID user(Jwt jwt) { try { return UUID.fromString(jwt.getSubject()); } catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatusCode.valueOf(401),"Invalid access token."); } }
}
