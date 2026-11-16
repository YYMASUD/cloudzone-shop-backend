package com.cloudzone.shopbackend.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /** Active variants of one product, cheapest first with an id tie-breaker. */
    List<ProductVariant> findAllByProductIdAndActiveTrueOrderByPriceAscIdAsc(Long productId);
}
