package com.civilmate.api.library;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.civilmate.api.auth.dto.ApiEnvelope;
import com.civilmate.api.library.dto.LibraryDtos.BookmarkRequest;
import com.civilmate.api.library.dto.LibraryDtos.BookmarkView;
import com.civilmate.api.library.dto.LibraryDtos.PageView;
import com.civilmate.api.library.service.BookmarkService;

@Validated
@RestController
@RequestMapping("/api/v1/bookmarks")
public class BookmarkController {
    private final BookmarkService bookmarks;
    public BookmarkController(BookmarkService bookmarks) { this.bookmarks = bookmarks; }

    @GetMapping
    public ApiEnvelope<PageView<BookmarkView>> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiEnvelope.success("Bookmarks retrieved successfully.", bookmarks.list(userId(jwt), type, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<BookmarkView> add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookmarkRequest request) {
        return ApiEnvelope.success("Bookmark saved successfully.", bookmarks.add(userId(jwt), request));
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        bookmarks.remove(userId(jwt), id);
        return ApiEnvelope.success("Bookmark removed successfully.", null);
    }

    private static UUID userId(Jwt jwt) {
        try { return UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException exception) { throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token."); }
    }
}
