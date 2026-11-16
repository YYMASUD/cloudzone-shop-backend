package com.cloudzone.shopbackend.catalog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Category> findAllByActiveTrueAndParentIsNull(Pageable pageable);

    Page<Category> findAllByActiveTrueAndParentId(Long parentId, Pageable pageable);
}
