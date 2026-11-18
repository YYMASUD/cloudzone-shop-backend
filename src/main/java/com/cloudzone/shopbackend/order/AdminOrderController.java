package com.cloudzone.shopbackend.order;

import com.cloudzone.shopbackend.common.web.PageResponse;
import com.cloudzone.shopbackend.payment.PaymentResponse;
import com.cloudzone.shopbackend.payment.PaymentService;
import com.cloudzone.shopbackend.payment.RefundRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin order desk: the fulfilment pipeline (status moves) and monetary reversals
 * (refunds). The whole {@code /api/admin/**} namespace is role-gated to ADMIN by
 * {@code SecurityConfig}; this controller only validates input. Orders here are
 * addressed by numeric id (not the customer-facing order number) because the admin
 * surface is not enumerable-sensitive the way the shopper surface is.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public AdminOrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    /** All orders, newest first; oversized {@code size} is clamped. */
    @GetMapping
    public PageResponse<OrderResponse> list(Pageable pageable) {
        return orderService.listAll(pageable);
    }

    /** One order by id, with its lines; unknown is a 404. */
    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.getById(id);
    }

    /**
     * Moves an order along the fulfilment pipeline (PAID → PROCESSING → SHIPPED →
     * DELIVERED) or cancels it. Illegal transitions are a 409.
     */
    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return orderService.updateStatus(id, request.status());
    }

    /**
     * Refunds a captured payment and restocks the order. A null {@code amount}
     * refunds the full captured total; a positive value is a partial refund.
     */
    @PostMapping("/{id}/refund")
    public PaymentResponse refund(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RefundRequest request) {
        return paymentService.refund(id, request);
    }

    /**
     * Marks a COD (or open online) payment collected. The courier-facing counterpart
     * to the customer's {@code POST /api/orders/{n}/pay}: it settles the outstanding
     * payment row on a DELIVERED COD order, or forces capture on a still-PENDING one.
     */
    @PostMapping("/{id}/collect")
    public PaymentResponse collect(@PathVariable Long id) {
        return paymentService.captureForAdmin(id);
    }
}
