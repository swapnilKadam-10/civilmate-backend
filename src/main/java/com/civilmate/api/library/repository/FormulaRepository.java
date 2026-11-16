package com.civilmate.api.library.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.civilmate.api.library.entity.FormulaEntity;

public interface FormulaRepository extends JpaRepository<FormulaEntity, UUID> {
    Optional<FormulaEntity> findBySlugAndStatus(String slug, com.civilmate.api.library.entity.ContentStatus status);
    Page<FormulaEntity> findAllByStatusOrderByTitleAsc(com.civilmate.api.library.entity.ContentStatus status, Pageable pageable);
    Page<FormulaEntity> findAllByOrderByTitleAsc(Pageable pageable);
    boolean existsBySlugAndIdNot(String slug, UUID id);
}
