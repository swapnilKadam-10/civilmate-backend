package com.civilmate.api.library.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.civilmate.api.library.entity.CodeReferenceEntity;
import com.civilmate.api.library.entity.ContentStatus;

public interface CodeReferenceRepository extends JpaRepository<CodeReferenceEntity, UUID> {
    Optional<CodeReferenceEntity> findByIdAndStatus(UUID id, ContentStatus status);
    Page<CodeReferenceEntity> findAllByStatusOrderByCodeNumberAsc(ContentStatus status, Pageable pageable);
    Page<CodeReferenceEntity> findAllByOrderByCodeNumberAsc(Pageable pageable);
}
