package com.cloudzone.shopbackend.shipping;

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
 * Admin shipping method management. The whole {@code /api/admin/**} namespace is gated
 * to ADMIN by {@code SecurityConfig}, so this controller only validates input — no role
 * checks needed here.
 */
@RestController
@RequestMapping("/api/admin/shipping-methods")
public class AdminShippingMethodController {

    private final ShippingMethodService shippingMethodService;

    public AdminShippingMethodController(ShippingMethodService shippingMethodService) {
        this.shippingMethodService = shippingMethodService;
    }

    /** All methods (inactive included), newest first; oversized {@code size} is clamped. */
    @GetMapping
    public PageResponse<ShippingMethodResponse> list(Pageable pageable) {
        return shippingMethodService.list(pageable);
    }

    /** One method by id; unknown is a 404. */
    @GetMapping("/{id}")
    public ShippingMethodResponse get(@PathVariable Long id) {
        return shippingMethodService.get(id);
    }

    /** Creates a method; a duplicate code is a 409 {@code shipping_code_taken}. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShippingMethodResponse create(@Valid @RequestBody ShippingMethodCreateRequest request) {
        return shippingMethodService.create(request);
    }

    /** Partial update; {@code null} fields leave the stored value untouched. */
    @PatchMapping("/{id}")
    public ShippingMethodResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ShippingMethodUpdateRequest request) {
        return shippingMethodService.update(id, request);
    }
}