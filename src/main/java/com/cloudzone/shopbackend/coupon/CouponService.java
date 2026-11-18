package com.cloudzone.shopbackend.coupon;

import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.common.web.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coupon logic: validating a code against an order subtotal, and the admin CRUD.
 *
 * <p>An unknown, inactive, expired or exhausted code all yield the same 404
 * {@code coupon_not_found}: no distinction means no way to probe which of those a
 * code is, so codes are not enumerable. A valid code whose {@code min_order_total}
 * is not met is a 400 {@code coupon_min_total}.
 *
 * <p>Discount rules: {@code PERCENT} yields {@code subtotal * value / 100} (value is
 * validated to [0, 100] at create/update); {@code FIXED} yields {@code min(value, subtotal)}
 * — a coupon never discounts below zero, and never exceeds the order value.
 */
@Service
public class CouponService {

    /** Hard ceiling on {@code size}; pages larger than this are clamped, not rejected. */
    private static final int MAX_PAGE_SIZE = 100;

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    // ── shopper-facing ────────────────────────────────────────────────────────

    /**
     * Resolves a code against an order subtotal, returning only the public shape.
     *
     * @param subtotal order subtotal; when non-null and below {@code min_order_total},
     *     the call is a 400 {@code coupon_min_total} rather than a silent success
     */
    @Transactional(readOnly = true)
    public CouponResponse validate(String code, BigDecimal subtotal) {
        Coupon coupon = usableCoupon(code);
        if (coupon.getMinOrderTotal() != null
                && subtotal != null
                && subtotal.compareTo(coupon.getMinOrderTotal()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "coupon_min_total",
                    "Order total is below the minimum for that code");
        }
        return CouponResponse.from(coupon);
    }

    /**
     * The monetary discount a code yields for a subtotal, for use inside checkout.
     * Applies the same usability rules as {@link #validate}; {@code null} subtotal is
     * treated as zero.
     */
    @Transactional(readOnly = true)
    public BigDecimal discountFor(String code, BigDecimal subtotal) {
        Coupon coupon = usableCoupon(code);
        BigDecimal total = subtotal == null ? BigDecimal.ZERO : subtotal;
        if (coupon.getMinOrderTotal() != null
                && total.compareTo(coupon.getMinOrderTotal()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "coupon_min_total",
                    "Order total is below the minimum for that code");
        }
        return switch (coupon.getDiscountType()) {
            case PERCENT -> total
                    .multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100));
            case FIXED -> coupon.getDiscountValue().min(total);
        };
    }

    // ── admin-facing ──────────────────────────────────────────────────────────

    /** Newest-first admin listing, paged with a hard size cap. */
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> list(Pageable pageable) {
        Pageable effective = PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE));
        Page<CouponResponse> page = couponRepository
                .findAllByOrderByCreatedAtDescIdDesc(effective)
                .map(CouponResponse::from);
        return PageResponse.from(page);
    }

    /** One coupon by id; unknown is a 404. */
    @Transactional(readOnly = true)
    public CouponResponse get(Long id) {
        return CouponResponse.from(coupon(id));
    }

    /**
     * Creates a coupon; a duplicate code (case-insensitive) is a 409. {@code active}
     * defaults to true and a PERCENT value is bounded to [0, 100].
     */
    @Transactional
    public CouponResponse create(CouponCreateRequest request) {
        assertPercentValue(request.discountType(), request.discountValue());
        if (couponRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "coupon_code_taken",
                    "A coupon with that code already exists");
        }
        Coupon coupon = couponRepository.save(Coupon.builder()
                .code(request.code().trim())
                .description(request.description())
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .minOrderTotal(request.minOrderTotal())
                .maxRedemptions(request.maxRedemptions())
                .timesRedeemed(0)
                .expiresAt(request.expiresAt())
                .active(request.active() == null || request.active())
                .build());
        return CouponResponse.from(coupon);
    }

    /** Applies the non-null fields of the patch; {@code active} defaults to true when absent. */
    @Transactional
    public CouponResponse update(Long id, CouponUpdateRequest patch) {
        Coupon coupon = coupon(id);
        assertPercentValue(
                patch.discountType() == null ? coupon.getDiscountType() : patch.discountType(),
                patch.discountValue() == null ? coupon.getDiscountValue() : patch.discountValue());
        if (patch.description() != null) {
            coupon.setDescription(patch.description());
        }
        if (patch.discountType() != null) {
            coupon.setDiscountType(patch.discountType());
        }
        if (patch.discountValue() != null) {
            coupon.setDiscountValue(patch.discountValue());
        }
        if (patch.minOrderTotal() != null) {
            coupon.setMinOrderTotal(patch.minOrderTotal());
        }
        if (patch.maxRedemptions() != null) {
            coupon.setMaxRedemptions(patch.maxRedemptions());
        }
        if (patch.expiresAt() != null) {
            coupon.setExpiresAt(patch.expiresAt());
        }
        if (patch.active() != null) {
            coupon.setActive(patch.active());
        }
        return CouponResponse.from(coupon);
    }

    /** Increments the redemption counter — called by the checkout flow, not by clients. */
    @Transactional
    public void recordRedemption(Long couponId) {
        Coupon coupon = coupon(couponId);
        coupon.setTimesRedeemed(coupon.getTimesRedeemed() + 1);
    }

    /** A code redeemed inside checkout: the coupon row plus the monetary discount. */
    public record Redemption(Coupon coupon, BigDecimal discount) {
    }

    /**
     * Validates a code and redeems it in one step for the checkout flow: applies the
     * same usability and {@code min_order_total} rules as {@link #discountFor}, then
     * increments {@code times_redeemed} on the loaded entity so the counter flushes
     * with the surrounding checkout transaction.
     *
     * @return the usable coupon and its discount (already rounded to 2 decimals and
     *     capped at the subtotal), or throws 404 {@code coupon_not_found} /
     *     400 {@code coupon_min_total} exactly like {@link #discountFor}
     */
    @Transactional
    public Redemption redeemForDiscount(String code, BigDecimal subtotal) {
        Coupon coupon = usableCoupon(code);
        BigDecimal total = subtotal == null ? BigDecimal.ZERO : subtotal;
        if (coupon.getMinOrderTotal() != null
                && total.compareTo(coupon.getMinOrderTotal()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "coupon_min_total",
                    "Order total is below the minimum for that code");
        }
        BigDecimal discount = switch (coupon.getDiscountType()) {
            case PERCENT -> total
                    .multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED -> coupon.getDiscountValue().min(total);
        };
        coupon.setTimesRedeemed(coupon.getTimesRedeemed() + 1);
        return new Redemption(coupon, discount);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    /**
     * Loads a code that a shopper may use right now: active, unexpired, and not
     * redeemed out. Every failure maps to a distinct 404/400 code.
     */
    private Coupon usableCoupon(String code) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code == null ? "" : code.trim())
                .orElseThrow(this::unknownCoupon);
        if (!coupon.isActive()
                || (coupon.getExpiresAt() != null
                        && coupon.getExpiresAt().isBefore(OffsetDateTime.now()))
                || (coupon.getMaxRedemptions() != null
                        && coupon.getTimesRedeemed() >= coupon.getMaxRedemptions())) {
            // Same code/message for every unusable case: no coupon-state probing.
            throw unknownCoupon();
        }
        return coupon;
    }

    private void assertPercentValue(CouponDiscountType type, BigDecimal value) {
        if (type == CouponDiscountType.PERCENT
                && value != null
                && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_discount_value",
                    "A percentage discount cannot exceed 100");
        }
    }

    private Coupon coupon(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "coupon_not_found", "Unknown coupon: " + id));
    }

    private ApiException unknownCoupon() {
        return new ApiException(HttpStatus.NOT_FOUND, "coupon_not_found",
                "Unknown or unusable coupon code");
    }
}