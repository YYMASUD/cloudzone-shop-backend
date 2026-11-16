package com.cloudzone.shopbackend.inventory;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    /**
     * Total quantity currently held against a product's own (unvarianted) stock.
     * Only {@code variant_id IS NULL} rows count — variant holds draw on the variant's
     * stock, not the parent product's.
     */
    @Query("""
            SELECT COALESCE(SUM(r.quantity), 0) FROM StockReservation r
            WHERE r.status = :status
              AND r.productId = :productId
              AND r.variantId IS NULL
            """)
    long sumActiveForProduct(
            @Param("status") StockReservationStatus status,
            @Param("productId") Long productId);

    /** Total quantity currently held against one specific variant. */
    @Query("""
            SELECT COALESCE(SUM(r.quantity), 0) FROM StockReservation r
            WHERE r.status = :status
              AND r.productId = :productId
              AND r.variantId = :variantId
            """)
    long sumActiveForVariant(
            @Param("status") StockReservationStatus status,
            @Param("productId") Long productId,
            @Param("variantId") Long variantId);

    /** The caller's reservation history, newest first; {@code id DESC} breaks ties. */
    List<StockReservation> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** ACTIVE holds whose deadline has passed — the input to the expiry sweep. */
    List<StockReservation> findAllByStatusAndExpiresAtBefore(
            StockReservationStatus status, OffsetDateTime cutoff);
}
