package com.cloudzone.shopbackend.catalog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Brand> findAllByActiveTrue(Pageable pageable);
}
