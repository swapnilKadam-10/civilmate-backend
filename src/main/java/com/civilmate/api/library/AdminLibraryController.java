package com.civilmate.api.library;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.civilmate.api.auth.dto.ApiEnvelope;
import com.civilmate.api.library.dto.LibraryDtos.CategoryView;
import com.civilmate.api.library.dto.LibraryDtos.CategoryWriteRequest;
import com.civilmate.api.library.dto.LibraryDtos.CodeView;
import com.civilmate.api.library.dto.LibraryDtos.CodeWriteRequest;
import com.civilmate.api.library.dto.LibraryDtos.FormulaView;
import com.civilmate.api.library.dto.LibraryDtos.FormulaWriteRequest;
import com.civilmate.api.library.service.LibraryService;

@RestController
@RequestMapping("/api/v1/admin/library")
public class AdminLibraryController {
    private final LibraryService library;
    public AdminLibraryController(LibraryService library) { this.library = library; }

    @GetMapping("/categories")
    public ApiEnvelope<?> listCategories() { return ApiEnvelope.success("Admin categories retrieved.", library.categories(null)); }

    @GetMapping("/formulas")
    public ApiEnvelope<?> listFormulas(@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return ApiEnvelope.success("Admin formulas retrieved.", library.adminFormulas(status, page, size)); }
    @GetMapping("/codes")
    public ApiEnvelope<?> listCodes(@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return ApiEnvelope.success("Admin code references retrieved.", library.adminCodes(status, page, size)); }

    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<CategoryView> createCategory(@Valid @RequestBody CategoryWriteRequest request) { return ApiEnvelope.success("Category created.", library.createCategory(request)); }
    @PutMapping("/categories/{id}")
    public ApiEnvelope<CategoryView> updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryWriteRequest request) { return ApiEnvelope.success("Category updated.", library.updateCategory(id, request)); }
    @DeleteMapping("/categories/{id}")
    public ApiEnvelope<Void> deleteCategory(@PathVariable UUID id) { library.deleteCategory(id); return ApiEnvelope.success("Category deleted.", null); }

    @PostMapping("/formulas") @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<FormulaView> createFormula(@Valid @RequestBody FormulaWriteRequest request) { return ApiEnvelope.success("Formula created.", library.createFormula(request)); }
    @PutMapping("/formulas/{id}")
    public ApiEnvelope<FormulaView> updateFormula(@PathVariable UUID id, @Valid @RequestBody FormulaWriteRequest request) { return ApiEnvelope.success("Formula updated.", library.updateFormula(id, request)); }
    @DeleteMapping("/formulas/{id}")
    public ApiEnvelope<Void> deleteFormula(@PathVariable UUID id) { library.archiveFormula(id); return ApiEnvelope.success("Formula archived.", null); }

    @PostMapping("/codes") @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<CodeView> createCode(@Valid @RequestBody CodeWriteRequest request) { return ApiEnvelope.success("Code reference created.", library.createCode(request)); }
    @PutMapping("/codes/{id}")
    public ApiEnvelope<CodeView> updateCode(@PathVariable UUID id, @Valid @RequestBody CodeWriteRequest request) { return ApiEnvelope.success("Code reference updated.", library.updateCode(id, request)); }
    @DeleteMapping("/codes/{id}")
    public ApiEnvelope<Void> deleteCode(@PathVariable UUID id) { library.archiveCode(id); return ApiEnvelope.success("Code reference archived.", null); }
}
