package com.civilmate.api.flashcards;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
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
import com.civilmate.api.flashcards.FlashcardDtos.*;

@RestController
@RequestMapping("/api/v1/admin/flashcards")
public class AdminFlashcardController {
    private final FlashcardService service;
    public AdminFlashcardController(FlashcardService service) { this.service=service; }
    @GetMapping("/categories") public ApiEnvelope<?> categories() { return ApiEnvelope.success("Flashcard categories retrieved.",service.categories()); }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED) public ApiEnvelope<Category> createCategory(@Valid @RequestBody CategoryWrite request) { return ApiEnvelope.success("Flashcard category created.",service.createCategory(request)); }
    @PutMapping("/categories/{id}") public ApiEnvelope<Category> updateCategory(@PathVariable UUID id,@Valid @RequestBody CategoryWrite request) { return ApiEnvelope.success("Flashcard category updated.",service.updateCategory(id,request)); }
    @DeleteMapping("/categories/{id}") public ApiEnvelope<Void> deleteCategory(@PathVariable UUID id) { service.deleteCategory(id);return ApiEnvelope.success("Flashcard category deleted.",null); }
    @GetMapping public ApiEnvelope<Page> list(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return ApiEnvelope.success("Flashcards retrieved.",service.adminCards(status,page,size)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiEnvelope<Card> create(@Valid @RequestBody CardWrite request) { return ApiEnvelope.success("Flashcard created.",service.createCard(request)); }
    @PutMapping("/{id}") public ApiEnvelope<Card> update(@PathVariable UUID id,@Valid @RequestBody CardWrite request) { return ApiEnvelope.success("Flashcard updated.",service.updateCard(id,request)); }
    @DeleteMapping("/{id}") public ApiEnvelope<Void> archive(@PathVariable UUID id) { service.archive(id);return ApiEnvelope.success("Flashcard archived.",null); }
}
