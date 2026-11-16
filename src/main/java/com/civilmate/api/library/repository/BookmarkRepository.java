package com.civilmate.api.library.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.civilmate.api.library.entity.BookmarkEntity;

public interface BookmarkRepository extends JpaRepository<BookmarkEntity, UUID> {
    List<BookmarkEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<BookmarkEntity> findByUserIdAndResourceTypeOrderByCreatedAtDesc(UUID userId, String resourceType);
    Page<BookmarkEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<BookmarkEntity> findAllByUserIdAndResourceTypeOrderByCreatedAtDesc(UUID userId, String resourceType, Pageable pageable);
    Optional<BookmarkEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<BookmarkEntity> findByUserIdAndResourceTypeAndResourceId(UUID userId, String resourceType, UUID resourceId);
    boolean existsByUserIdAndResourceTypeAndResourceId(UUID userId, String resourceType, UUID resourceId);
}
