package com.cloudzone.shopbackend.coupon;

import com.cloudzone.shopbackend.common.error.ApiException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shopper-facing coupon preview. Requires a valid token — the endpoint is not in
 * {@code SecurityConfig}'s public list, matching the catalogue posture.
 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /**
     * Validates {@code code} for an order of {@code subtotal} and returns the discount
     * mechanics; an unknown/unusable code is 404, an undersized order is 400.
     */
    @GetMapping("/{code}")
    public CouponResponse validate(
            @PathVariable String code,
            @RequestParam(name = "subtotal", required = false) BigDecimal subtotal) {
        if (subtotal != null && subtotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_parameter",
                    "subtotal must be zero or positive");
        }
        return couponService.validate(code, subtotal);
    }
}