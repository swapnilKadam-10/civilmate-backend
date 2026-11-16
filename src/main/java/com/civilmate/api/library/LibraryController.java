package com.civilmate.api.library;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.civilmate.api.auth.dto.ApiEnvelope;
import com.civilmate.api.library.dto.LibraryDtos.CategoryView;
import com.civilmate.api.library.dto.LibraryDtos.CodeView;
import com.civilmate.api.library.dto.LibraryDtos.FormulaView;
import com.civilmate.api.library.service.LibraryService;

@Validated
@RestController
@RequestMapping("/api/v1")
public class LibraryController {
    private final LibraryService library;
    private final com.civilmate.api.library.repository.CategoryRepository categories;
    public LibraryController(LibraryService library, com.civilmate.api.library.repository.CategoryRepository categories) { this.library = library; this.categories = categories; }

    @GetMapping("/categories")
    public ApiEnvelope<List<CategoryView>> categories(@RequestParam(required = false) String type) {
        return ApiEnvelope.success("Categories retrieved successfully.", library.categories(type));
    }

    @GetMapping("/categories/{id}")
    public ApiEnvelope<CategoryView> category(@PathVariable UUID id) { return ApiEnvelope.success("Category retrieved successfully.", library.category(id)); }

    @GetMapping("/formulas")
    public ApiEnvelope<?> formulas(@RequestParam(required = false) String q, @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "title") String sort) {
        return ApiEnvelope.success("Formulas retrieved successfully.", library.formulas(q, category, page, size, checkedSort(sort)));
    }

    @GetMapping("/formulas/search")
    public ApiEnvelope<?> searchFormulas(@RequestParam String q, @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "title") String sort) {
        return formulas(q, category, page, size, sort);
    }

    @GetMapping("/formulas/category/{categoryId}")
    public ApiEnvelope<?> formulasByCategory(@PathVariable UUID categoryId, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "title") String sort) {
        String slug = categories.findById(categoryId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found.")).getSlug();
        return formulas(q, slug, page, size, sort);
    }

    @GetMapping("/formulas/{id}")
    public ApiEnvelope<FormulaView> formula(@PathVariable UUID id) { return ApiEnvelope.success("Formula retrieved successfully.", library.formula(id)); }

    @GetMapping("/codes")
    public ApiEnvelope<?> codes(@RequestParam(required = false) String q, @RequestParam(required = false) String category,
            @RequestParam(required = false) String standard, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, @RequestParam(defaultValue = "title") String sort) {
        return ApiEnvelope.success("Code references retrieved successfully.", library.codes(q, category, standard, page, size, checkedSort(sort)));
    }

    @GetMapping("/codes/search")
    public ApiEnvelope<?> searchCodes(@RequestParam String q, @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiEnvelope.success("Code references retrieved successfully.", library.codes(q, category, null, page, size, "title"));
    }

    @GetMapping("/codes/standard/{codeNumber}")
    public ApiEnvelope<?> codesByStandard(@PathVariable String codeNumber, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiEnvelope.success("Code references retrieved successfully.", library.codes(null, null, codeNumber, page, size, "title"));
    }

    @GetMapping("/codes/{id}")
    public ApiEnvelope<CodeView> code(@PathVariable UUID id) { return ApiEnvelope.success("Code reference retrieved successfully.", library.code(id)); }

    private static String checkedSort(String sort) {
        if (!List.of("title", "recent").contains(sort)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort must be title or recent.");
        return sort;
    }
}
