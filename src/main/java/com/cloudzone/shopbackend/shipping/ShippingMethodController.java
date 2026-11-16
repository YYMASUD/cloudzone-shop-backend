package com.cloudzone.shopbackend.shipping;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shopper-facing shipping method listing. Requires a valid token — the endpoint is not
 * in {@code SecurityConfig}'s public list, matching the catalogue posture.
 */
@RestController
@RequestMapping("/api/shipping-methods")
public class ShippingMethodController {

    private final ShippingMethodService shippingMethodService;

    public ShippingMethodController(ShippingMethodService shippingMethodService) {
        this.shippingMethodService = shippingMethodService;
    }

    /** Active methods, cheapest first; checkout picks one of these at shipping time. */
    @GetMapping
    public List<ShippingMethodResponse> listActive() {
        return shippingMethodService.listActive();
    }
}