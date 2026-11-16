package com.cloudzone.shopbackend.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LowStockAlertRepository extends JpaRepository<LowStockAlert, Long> {

    List<LowStockAlert> findAllByAcknowledgedAtIsNullOrderByCreatedAtDescIdDesc();

    boolean existsByProductIdAndVariantIdAndAcknowledgedAtIsNull(
            Long productId, Long variantId);
}
