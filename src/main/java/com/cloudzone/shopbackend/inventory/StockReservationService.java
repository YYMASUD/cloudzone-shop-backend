package com.cloudzone.shopbackend.inventory;

import com.cloudzone.shopbackend.catalog.Product;
import com.cloudzone.shopbackend.catalog.ProductRepository;
import com.cloudzone.shopbackend.catalog.ProductVariant;
import com.cloudzone.shopbackend.catalog.ProductVariantRepository;
import com.cloudzone.shopbackend.common.error.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The stock reservation ledger's write side: hold, release, commit, expire.
 *
 * <p>Why a ledger instead of decrementing {@code stock_quantity} on hold: a decrement is
 * a write that must be undone if the shopper abandons checkout — and "undo on timeout"
 * needs a second write that can itself fail, leaving stock permanently leaked. Summing
 * {@code ACTIVE} reservations instead makes availability a pure read; the only state
 * transition is ACTIVE → COMMITTED/RELEASED, and the expiry sweep is idempotent.
 */
@Service
public class StockReservationService {

    private final StockReservationRepository reservationRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    public StockReservationService(
            StockReservationRepository reservationRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository) {
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    /**
     * Places a hold if the requested quantity is available, rejecting the attempt with a
     * 409 otherwise. The caller's id is stamped onto the row so only they can release or
     * commit it later.
     *
     * @param userId  authenticated caller ({@code users.id}); may be null for system holds
     * @param request product/variant, quantity, and optional hold TTL in minutes
     */
    @Transactional
    public StockReservationResponse hold(Long userId, HoldStockRequest request) {
        Product product = productRepository.findById(request.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> unknownProduct(request.productId()));

        Long variantId = request.variantId();
        int available;
        if (variantId == null) {
            available = product.getStockQuantity();
        } else {
            ProductVariant variant = variantRepository.findById(variantId)
                    .filter(v -> v.isActive()
                            && v.getProduct().getId().equals(product.getId()))
                    .orElseThrow(() -> unknownVariant(variantId));
            available = variant.getStockQuantity();
        }

        long reserved = variantId == null
                ? reservationRepository.sumActiveForProduct(
                        StockReservationStatus.ACTIVE, product.getId())
                : reservationRepository.sumActiveForVariant(
                        StockReservationStatus.ACTIVE, product.getId(), variantId);

        if (available - reserved < request.quantity()) {
            throw new ApiException(HttpStatus.CONFLICT, "insufficient_stock",
                    "Only %d unit(s) available for product %d".formatted(
                            available - reserved, product.getId()));
        }

        OffsetDateTime expiresAt = request.ttlMinutes() == null
                ? null
                : OffsetDateTime.now().plusMinutes(request.ttlMinutes());

        StockReservation saved = reservationRepository.save(StockReservation.builder()
                .userId(userId)
                .productId(product.getId())
                .variantId(variantId)
                .quantity(request.quantity())
                .status(StockReservationStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build());
        return StockReservationResponse.from(saved);
    }

    /**
     * Gives a hold back. Owner-only (a foreign id is indistinguishable from a missing one
     * — 404 either way, so reservation ids of other users are not enumerable) and only
     * ACTIVE holds can transition; re-releasing is a client bug, not a new release.
     */
    @Transactional
    public StockReservationResponse release(Long userId, Long reservationId) {
        StockReservation reservation = ownedActive(userId, reservationId);
        reservation.setStatus(StockReservationStatus.RELEASED);
        return StockReservationResponse.from(reservation);
    }

    /**
     * Converts a hold into a committed allocation for the given order — the checkout
     * flow calls this once the order row exists. Committed rows keep counting only as
     * history; the order itself now owns the stock.
     */
    @Transactional
    public StockReservationResponse commit(Long userId, Long reservationId, Long orderId) {
        StockReservation reservation = ownedActive(userId, reservationId);
        reservation.setOrderId(orderId);
        reservation.setStatus(StockReservationStatus.COMMITTED);
        return StockReservationResponse.from(reservation);
    }

    /** The caller's own reservations, newest first. */
    @Transactional(readOnly = true)
    public List<StockReservationResponse> history(Long userId) {
        return reservationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId)
                .stream()
                .map(StockReservationResponse::from)
                .toList();
    }

    /**
     * Releases every still-ACTIVE hold that belongs to an order — used when a
     * PENDING order is cancelled before payment captured. Committed rows are left
     * untouched (they no longer subtract from availability; the order owns that
     * stock, which {@link #restockForOrder} returns to the shelf).
     */
    @Transactional
    public void releaseAllForOrder(Long orderId) {
        for (StockReservation reservation : reservationRepository.findAllByOrderId(orderId)) {
            if (reservation.getStatus() == StockReservationStatus.ACTIVE) {
                reservation.setStatus(StockReservationStatus.RELEASED);
            }
        }
    }

    /**
     * Returns an order's committed stock to the shelf — used when a PAID or
     * PROCESSING order is cancelled or refunded. Each hold increments its
     * product's (or variant's) {@code stock_quantity} back and is demoted to
     * RELEASED so it stops being an outstanding allocation. Idempotent: rows
     * already RELEASED are skipped, so a repeated refund cannot double-restock.
     */
    @Transactional
    public void restockForOrder(Long orderId) {
        for (StockReservation reservation : reservationRepository.findAllByOrderId(orderId)) {
            if (reservation.getStatus() != StockReservationStatus.COMMITTED) {
                continue;
            }
            if (reservation.getVariantId() == null) {
                productRepository.findById(reservation.getProductId())
                        .ifPresent(p -> p.setStockQuantity(
                                p.getStockQuantity() + reservation.getQuantity()));
            } else {
                variantRepository.findById(reservation.getVariantId())
                        .ifPresent(v -> v.setStockQuantity(
                                v.getStockQuantity() + reservation.getQuantity()));
            }
            reservation.setStatus(StockReservationStatus.RELEASED);
        }
    }

    /**
     * Demotes every expired ACTIVE hold to RELEASED. Idempotent and safe to run on a
     * schedule: rows already terminal are not selected, and a concurrently released row
     * simply saves a second time as RELEASED.
     *
     * @return how many holds were swept (for logging/metrics)
     */
    @Transactional
    public int releaseExpired() {
        List<StockReservation> expired = reservationRepository
                .findAllByStatusAndExpiresAtBefore(
                        StockReservationStatus.ACTIVE, OffsetDateTime.now());
        expired.forEach(r -> r.setStatus(StockReservationStatus.RELEASED));
        return expired.size();
    }

    private StockReservation ownedActive(Long userId, Long reservationId) {
        StockReservation reservation = reservationRepository.findById(reservationId)
                .filter(r -> userId != null && userId.equals(r.getUserId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "reservation_not_found",
                        "No active reservation %d for this user".formatted(reservationId)));
        if (reservation.getStatus() != StockReservationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "reservation_not_active",
                    "Reservation %d is %s, not ACTIVE".formatted(
                            reservationId, reservation.getStatus()));
        }
        return reservation;
    }

    private static ApiException unknownProduct(Long productId) {
        return new ApiException(HttpStatus.NOT_FOUND, "product_not_found",
                "Unknown or inactive product: " + productId);
    }

    private static ApiException unknownVariant(Long variantId) {
        return new ApiException(HttpStatus.NOT_FOUND, "variant_not_found",
                "Unknown or inactive variant: " + variantId);
    }
}
