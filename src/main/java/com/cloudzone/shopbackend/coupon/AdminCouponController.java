package com.cloudzone.shopbackend.coupon;

import com.cloudzone.shopbackend.common.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin coupon management. The whole {@code /api/admin/**} namespace is gated to
 * ADMIN by {@code SecurityConfig}, so this controller only validates input — no
 * role checks needed here.
 */
@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /** All coupons, newest first; oversized {@code size} is clamped by the service. */
    @GetMapping
    public PageResponse<CouponResponse> list(Pageable pageable) {
        return couponService.list(pageable);
    }

    /** One coupon by id; unknown is a 404. */
    @GetMapping("/{id}")
    public CouponResponse get(@PathVariable Long id) {
        return couponService.get(id);
    }

    /** Creates a coupon; a duplicate code is a 409 {@code coupon_code_taken}. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(@Valid @RequestBody CouponCreateRequest request) {
        return couponService.create(request);
    }

    /** Partial update; {@code null} fields leave the stored value untouched. */
    @PatchMapping("/{id}")
    public CouponResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CouponUpdateRequest request) {
        return couponService.update(id, request);
    }
}