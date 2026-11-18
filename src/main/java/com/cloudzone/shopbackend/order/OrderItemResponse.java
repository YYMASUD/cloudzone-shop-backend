package com.cloudzone.shopbackend.order;

import java.math.BigDecimal;

/** One line of an order — the immutable price snapshot stored with the order. */
public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        String productSku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductName(),
                item.getProductSku(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }
}
