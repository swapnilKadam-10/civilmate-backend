package com.civilmate.api.library.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.civilmate.api.library.dto.LibraryDtos.BookmarkRequest;
import com.civilmate.api.library.dto.LibraryDtos.BookmarkView;
import com.civilmate.api.library.dto.LibraryDtos.PageView;
import com.civilmate.api.library.entity.BookmarkEntity;
import com.civilmate.api.library.entity.CodeReferenceEntity;
import com.civilmate.api.library.entity.FormulaEntity;
import com.civilmate.api.library.repository.BookmarkRepository;
import com.civilmate.api.library.repository.CodeReferenceRepository;
import com.civilmate.api.library.repository.FormulaRepository;

@Service
public class BookmarkService {
    private final BookmarkRepository bookmarks;
    private final LibraryService library;
    private final FormulaRepository formulas;
    private final CodeReferenceRepository codes;
    public BookmarkService(BookmarkRepository bookmarks, LibraryService library, FormulaRepository formulas, CodeReferenceRepository codes) {
        this.bookmarks = bookmarks; this.library = library; this.formulas = formulas; this.codes = codes;
    }

    @Transactional
    public BookmarkView add(UUID userId, BookmarkRequest request) {
        String type = request.resourceType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("FORMULA", "CODE_REFERENCE").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resource type must be FORMULA or CODE_REFERENCE.");
        if (type.equals("FORMULA")) library.publishedFormulaEntity(request.resourceId());
        else library.publishedCodeEntity(request.resourceId());
        BookmarkEntity bookmark = bookmarks.findByUserIdAndResourceTypeAndResourceId(userId, type, request.resourceId()).orElseGet(() -> bookmarks.save(new BookmarkEntity(userId, type, request.resourceId(), Instant.now())));
        return view(bookmark);
    }

    @Transactional(readOnly = true)
    public PageView<BookmarkView> list(UUID userId, String resourceType, int page, int size) {
        String type = resourceType == null || resourceType.isBlank() ? null : resourceType.trim().toUpperCase(java.util.Locale.ROOT);
        if (type != null && !List.of("FORMULA", "CODE_REFERENCE").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resource type must be FORMULA or CODE_REFERENCE.");
        Page<BookmarkEntity> result = type == null ? bookmarks.findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                : bookmarks.findAllByUserIdAndResourceTypeOrderByCreatedAtDesc(userId, type, PageRequest.of(page, size));
        List<BookmarkView> content = result.getContent().stream().map(this::viewIfPublished).filter(java.util.Objects::nonNull).toList();
        return new PageView<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public void remove(UUID userId, UUID bookmarkId) {
        BookmarkEntity bookmark = bookmarks.findByIdAndUserId(bookmarkId, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bookmark not found."));
        bookmarks.delete(bookmark);
    }

    private BookmarkView viewIfPublished(BookmarkEntity bookmark) {
        if (bookmark.getResourceType().equals("FORMULA")) {
            return formulas.findById(bookmark.getResourceId()).filter(f -> f.getStatus() == com.civilmate.api.library.entity.ContentStatus.PUBLISHED)
                    .map(f -> viewFormula(bookmark, f)).orElse(null);
        }
        return codes.findById(bookmark.getResourceId()).filter(c -> c.getStatus() == com.civilmate.api.library.entity.ContentStatus.PUBLISHED)
                .map(c -> viewCode(bookmark, c)).orElse(null);
    }
    private BookmarkView view(BookmarkEntity bookmark) { return viewIfPublished(bookmark); }
    private BookmarkView viewFormula(BookmarkEntity bookmark, FormulaEntity formula) { return new BookmarkView(bookmark.getId(), bookmark.getResourceType(), bookmark.getResourceId(), formula.getTitle(), formula.getTopic(), formula.getCategory().getName(), bookmark.getCreatedAt()); }
    private BookmarkView viewCode(BookmarkEntity bookmark, CodeReferenceEntity code) { return new BookmarkView(bookmark.getId(), bookmark.getResourceType(), bookmark.getResourceId(), code.getTitle(), code.getTopic(), code.getCategory().getName(), bookmark.getCreatedAt()); }
}
