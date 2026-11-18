package com.cloudzone.shopbackend.order;

import com.cloudzone.shopbackend.common.security.JwtService;
import com.cloudzone.shopbackend.common.web.PageResponse;
import com.cloudzone.shopbackend.payment.PaymentResponse;
import com.cloudzone.shopbackend.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order placement and lookup for the authenticated shopper.
 *
 * <p>Like the cart, every route resolves the caller's id from the verified token —
 * there is no {@code /api/orders/{userId}} to forge, and a foreign order number is
 * indistinguishable from a missing one (404, so orders are not enumerable).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final JwtService jwtService;

    public OrderController(OrderService orderService, PaymentService paymentService,
            JwtService jwtService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.jwtService = jwtService;
    }

    /**
     * Checks out the caller's cart: validates it, holds stock, redeems the optional
     * coupon, and persists the order with an {@code INITIATED} payment — all atomically.
     */
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(
            @Valid @RequestBody CheckoutRequest request,
            JwtAuthenticationToken authentication) {
        return orderService.checkout(userId(authentication), request);
    }

    /** The caller's orders, newest first. */
    @GetMapping
    public PageResponse<OrderResponse> list(
            Pageable pageable, JwtAuthenticationToken authentication) {
        return orderService.list(userId(authentication), pageable);
    }

    /** One of the caller's orders by its human-facing order number. */
    @GetMapping("/{orderNumber}")
    public OrderResponse get(
            @PathVariable String orderNumber, JwtAuthenticationToken authentication) {
        return orderService.get(userId(authentication), orderNumber);
    }

    /**
     * Captures payment for the caller's PENDING order. The provider is resolved
     * from the payment method chosen at checkout; a successful capture moves the
     * order to PAID and dispatches an ORDER_PAID notification.
     */
    @PostMapping("/{orderNumber}/pay")
    public PaymentResponse pay(
            @PathVariable String orderNumber, JwtAuthenticationToken authentication) {
        return paymentService.captureForOwner(userId(authentication), orderNumber);
    }

    /**
     * Cancels one of the caller's orders before it ships. Releases the stock holds
     * for a PENDING order, or restocks a PAID one; a monetary reversal for a paid
     * order goes through the admin refund path instead.
     */
    @PostMapping("/{orderNumber}/cancel")
    public OrderResponse cancel(
            @PathVariable String orderNumber, JwtAuthenticationToken authentication) {
        return orderService.cancel(userId(authentication), orderNumber);
    }

    private long userId(JwtAuthenticationToken authentication) {
        return jwtService.toPrincipal(authentication.getToken()).userId();
    }
}
