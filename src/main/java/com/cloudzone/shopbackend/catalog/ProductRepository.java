package com.cloudzone.shopbackend.catalog;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySku(String sku);

    Page<Product> findAllByActiveTrue(Pageable pageable);

    Page<Product> findAllByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    /**
     * The storefront listing query: every filter is optional and a null means "do not
     * constrain on this". Ids (not slugs) are bound because the service resolves slugs
     * first and turns an unknown value into a 404 rather than an empty page.
     *
     * <p>{@code LEFT JOIN} is explicit so a product without a category or brand still
     * appears when those filters are absent; an implicit path expression would silently
     * drop such rows. {@code q} is a case-insensitive substring match on name or
     * description — {@code %}/{@code _} in the term are treated as wildcards, which is
     * acceptable for a storefront search box and is not an injection vector (the term is
     * still a bound parameter).
     *
     * <p>{@code findAllByActiveTrue}/{@code findAllByCategoryIdAndActiveTrue} are kept
     * alongside because the schema-contract tests use them to prove the mapping and the
     * derived-query machinery; this method is what the service actually calls.
     */
    @Query("""
            SELECT p FROM Product p
            LEFT JOIN p.category c
            LEFT JOIN p.brand b
            WHERE p.active = true
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:brandId IS NULL OR b.id = :brandId)
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:q IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Product> search(
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("q") String q,
            Pageable pageable);
}
