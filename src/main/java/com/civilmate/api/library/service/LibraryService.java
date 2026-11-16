package com.civilmate.api.library.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.civilmate.api.library.dto.LibraryDtos.CategoryView;
import com.civilmate.api.library.dto.LibraryDtos.CategoryWriteRequest;
import com.civilmate.api.library.dto.LibraryDtos.PageView;
import com.civilmate.api.library.dto.LibraryDtos.CodeView;
import com.civilmate.api.library.dto.LibraryDtos.CodeWriteRequest;
import com.civilmate.api.library.dto.LibraryDtos.FormulaView;
import com.civilmate.api.library.dto.LibraryDtos.FormulaWriteRequest;
import com.civilmate.api.library.entity.CategoryEntity;
import com.civilmate.api.library.entity.CodeReferenceEntity;
import com.civilmate.api.library.entity.ContentStatus;
import com.civilmate.api.library.entity.FormulaEntity;
import com.civilmate.api.library.repository.CategoryRepository;
import com.civilmate.api.library.repository.CodeReferenceRepository;
import com.civilmate.api.library.repository.FormulaRepository;

@Service
public class LibraryService {
    private final CategoryRepository categories;
    private final FormulaRepository formulas;
    private final CodeReferenceRepository codes;
    private final com.civilmate.api.library.repository.LibrarySearchRepository search;

    public LibraryService(CategoryRepository categories, FormulaRepository formulas, CodeReferenceRepository codes,
            com.civilmate.api.library.repository.LibrarySearchRepository search) {
        this.categories = categories; this.formulas = formulas; this.codes = codes; this.search = search;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> categories(String type) {
        String selected = type == null || type.isBlank() ? null : type.toUpperCase(Locale.ROOT);
        if (selected != null && !List.of("FORMULA", "CODE_REFERENCE", "BOTH").contains(selected)) throw badRequest("Unsupported category type.");
        return categories.findAllByOrderByNameAsc().stream().filter(c -> selected == null || c.getResourceType().equals("BOTH") || c.getResourceType().equals(selected)).map(CategoryView::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryView category(UUID id) { return CategoryView.from(categories.findById(id).orElseThrow(() -> notFound("Category not found."))); }

    @Transactional(readOnly = true)
    public PageView<FormulaView> adminFormulas(String status, int page, int size) {
        Page<FormulaEntity> result = status == null || status.isBlank() ? formulas.findAllByOrderByTitleAsc(PageRequest.of(page, size)) : formulas.findAllByStatusOrderByTitleAsc(status(status), PageRequest.of(page, size));
        return new PageView<>(result.getContent().stream().map(FormulaView::from).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true)
    public PageView<CodeView> adminCodes(String status, int page, int size) {
        Page<CodeReferenceEntity> result = status == null || status.isBlank() ? codes.findAllByOrderByCodeNumberAsc(PageRequest.of(page, size)) : codes.findAllByStatusOrderByCodeNumberAsc(status(status), PageRequest.of(page, size));
        return new PageView<>(result.getContent().stream().map(CodeView::from).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public com.civilmate.api.library.dto.LibraryDtos.PageView<com.civilmate.api.library.dto.LibraryDtos.FormulaCard> formulas(String q, String categorySlug, int page, int size, String sort) {
        return search.formulas(normalizeQuery(q), categorySlug, page, size, sort);
    }

    @Transactional(readOnly = true)
    public FormulaView formula(UUID id) {
        FormulaEntity formula = formulas.findById(id).filter(item -> item.getStatus() == ContentStatus.PUBLISHED).orElseThrow(() -> notFound("Formula not found."));
        return FormulaView.from(formula);
    }

    @Transactional(readOnly = true)
    public com.civilmate.api.library.dto.LibraryDtos.PageView<com.civilmate.api.library.dto.LibraryDtos.CodeCard> codes(String q, String categorySlug, String standard, int page, int size, String sort) {
        return search.codes(normalizeQuery(q), blankToNull(categorySlug), blankToNull(standard), page, size, sort);
    }

    @Transactional(readOnly = true)
    public CodeView code(UUID id) {
        CodeReferenceEntity code = codes.findById(id).filter(item -> item.getStatus() == ContentStatus.PUBLISHED).orElseThrow(() -> notFound("Code reference not found."));
        return CodeView.from(code);
    }

    @Transactional
    public CategoryView createCategory(CategoryWriteRequest request) {
        validateCategoryType(request.resourceType());
        return CategoryView.from(categories.save(new CategoryEntity(request.name().trim(), slug(request.slug()), request.subject().trim(), request.description(), request.resourceType().toUpperCase(Locale.ROOT), Instant.now())));
    }

    @Transactional
    public CategoryView updateCategory(UUID id, CategoryWriteRequest request) {
        CategoryEntity category = categories.findById(id).orElseThrow(() -> notFound("Category not found."));
        validateCategoryType(request.resourceType());
        category.update(request.name().trim(), slug(request.slug()), request.subject().trim(), request.description(), request.resourceType().toUpperCase(Locale.ROOT), Instant.now());
        return CategoryView.from(category);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        CategoryEntity category = categories.findById(id).orElseThrow(() -> notFound("Category not found."));
        if (formulas.findAll().stream().anyMatch(item -> item.getCategory().getId().equals(id)) || codes.findAll().stream().anyMatch(item -> item.getCategory().getId().equals(id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category is in use and cannot be deleted.");
        }
        categories.delete(category);
    }

    @Transactional
    public FormulaView createFormula(FormulaWriteRequest request) {
        CategoryEntity category = category(request.categoryId(), "FORMULA");
        validateFormula(request);
        FormulaEntity formula = new FormulaEntity(category, request.title().trim(), slug(request.slug()), request.topic().trim(), request.expression().trim(),
                request.variableDefinitions(), request.explanation().trim(), request.exampleCalculation(), request.sourceReference().trim(),
                request.difficulty().toUpperCase(Locale.ROOT), safeList(request.relatedFormulaIds()), status(request.status()), Instant.now());
        return FormulaView.from(formulas.save(formula));
    }

    @Transactional
    public FormulaView updateFormula(UUID id, FormulaWriteRequest request) {
        FormulaEntity formula = formulas.findById(id).orElseThrow(() -> notFound("Formula not found."));
        CategoryEntity category = category(request.categoryId(), "FORMULA");
        validateFormula(request);
        formula.update(category, request.title().trim(), slug(request.slug()), request.topic().trim(), request.expression().trim(), request.variableDefinitions(),
                request.explanation().trim(), request.exampleCalculation(), request.sourceReference().trim(), request.difficulty().toUpperCase(Locale.ROOT),
                safeList(request.relatedFormulaIds()), status(request.status()), Instant.now());
        return FormulaView.from(formula);
    }

    @Transactional
    public void archiveFormula(UUID id) { formulas.findById(id).orElseThrow(() -> notFound("Formula not found.")).archive(Instant.now()); }

    @Transactional
    public CodeView createCode(CodeWriteRequest request) {
        validateCode(request);
        CodeReferenceEntity code = new CodeReferenceEntity(category(request.categoryId(), "CODE_REFERENCE"), request.codeNumber().trim(), request.title().trim(),
                request.subject().trim(), request.topic().trim(), request.edition(), request.publicationYear(), nullableTrim(request.clauseIdentifier()), request.summary().trim(),
                request.permittedExcerpt(), request.sourceInformation().trim(), request.sourceUrl(), request.verificationLevel().toUpperCase(Locale.ROOT),
                safeList(request.relatedReferenceIds()), status(request.status()), Instant.now());
        return CodeView.from(codes.save(code));
    }

    @Transactional
    public CodeView updateCode(UUID id, CodeWriteRequest request) {
        CodeReferenceEntity code = codes.findById(id).orElseThrow(() -> notFound("Code reference not found."));
        validateCode(request);
        code.update(category(request.categoryId(), "CODE_REFERENCE"), request.codeNumber().trim(), request.title().trim(), request.subject().trim(), request.topic().trim(),
                request.edition(), request.publicationYear(), nullableTrim(request.clauseIdentifier()), request.summary().trim(), request.permittedExcerpt(),
                request.sourceInformation().trim(), request.sourceUrl(), request.verificationLevel().toUpperCase(Locale.ROOT), safeList(request.relatedReferenceIds()),
                status(request.status()), Instant.now());
        return CodeView.from(code);
    }

    @Transactional
    public void archiveCode(UUID id) { codes.findById(id).orElseThrow(() -> notFound("Code reference not found.")).archive(Instant.now()); }

    @Transactional(readOnly = true)
    public FormulaEntity publishedFormulaEntity(UUID id) { return formulas.findById(id).filter(item -> item.getStatus() == ContentStatus.PUBLISHED).orElseThrow(() -> notFound("Published formula not found.")); }
    @Transactional(readOnly = true)
    public CodeReferenceEntity publishedCodeEntity(UUID id) { return codes.findById(id).filter(item -> item.getStatus() == ContentStatus.PUBLISHED).orElseThrow(() -> notFound("Published code reference not found.")); }
    @Transactional(readOnly = true)
    public FormulaEntity formulaEntity(UUID id) { return formulas.findById(id).orElseThrow(() -> notFound("Formula not found.")); }
    @Transactional(readOnly = true)
    public CodeReferenceEntity codeEntity(UUID id) { return codes.findById(id).orElseThrow(() -> notFound("Code reference not found.")); }

    private CategoryEntity category(UUID id, String type) {
        CategoryEntity category = categories.findById(id).orElseThrow(() -> notFound("Category not found."));
        if (!category.getResourceType().equals("BOTH") && !category.getResourceType().equals(type)) throw badRequest("Category is not available for this resource type.");
        return category;
    }
    private static void validateCategoryType(String type) { if (!List.of("FORMULA", "CODE_REFERENCE", "BOTH").contains(type.toUpperCase(Locale.ROOT))) throw badRequest("Category type must be FORMULA, CODE_REFERENCE, or BOTH."); }
    private static void validateFormula(FormulaWriteRequest request) {
        if (!List.of("BEGINNER", "INTERMEDIATE", "ADVANCED").contains(request.difficulty().toUpperCase(Locale.ROOT))) throw badRequest("Difficulty must be BEGINNER, INTERMEDIATE, or ADVANCED.");
        status(request.status());
        if (request.relatedFormulaIds() != null && request.relatedFormulaIds().size() > 30) throw badRequest("A formula can list at most 30 related formulas.");
    }
    private static void validateCode(CodeWriteRequest request) {
        String verification = request.verificationLevel().toUpperCase(Locale.ROOT);
        if (!List.of("VERIFIED", "EDITORIAL_SUMMARY", "EDUCATIONAL_NOTE").contains(verification)) throw badRequest("Select a supported verification label.");
        if (request.clauseIdentifier() != null && !request.clauseIdentifier().isBlank() && !verification.equals("VERIFIED")) throw badRequest("A clause identifier can only be recorded on a reference explicitly marked verified.");
        if (request.permittedExcerpt() != null && request.permittedExcerpt().length() > 300) throw badRequest("Permitted excerpts are limited to 300 characters.");
        status(request.status());
        if (request.relatedReferenceIds() != null && request.relatedReferenceIds().size() > 30) throw badRequest("A code reference can list at most 30 related references.");
    }
    private static ContentStatus status(String value) {
        try { return ContentStatus.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw badRequest("Status must be DRAFT, REVIEW, PUBLISHED, or ARCHIVED."); }
    }
    private static String slug(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (normalized.isBlank()) throw badRequest("Slug must contain letters or numbers.");
        return normalized;
    }
    private static String normalizeQuery(String value) { return blankToNull(value); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String nullableTrim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static List<String> safeList(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
