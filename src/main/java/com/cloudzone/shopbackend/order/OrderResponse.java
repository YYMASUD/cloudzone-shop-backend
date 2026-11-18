package com.cloudzone.shopbackend.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The public shape of an order. Money fields are the checkout snapshot: later coupon,
 * price or method edits never rewrite them. {@code discountAmount} is zero and
 * {@code couponCode} null when no coupon was applied.
 */
public record OrderResponse(
        Long id,
        String orderNumber,
        String status,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        String couponCode,
        BigDecimal shippingCost,
        BigDecimal tax,
        BigDecimal total,
        String currency,
        AddressResponse shippingAddress,
        AddressResponse billingAddress,
        String notes,
        List<OrderItemResponse> items,
        OffsetDateTime createdAt) {

    /** The public shape of the address attached to an order. */
    public record AddressResponse(
            Long id,
            String label,
            String recipientName,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country) {

        static AddressResponse from(com.cloudzone.shopbackend.user.Address address) {
            if (address == null) {
                return null;
            }
            return new AddressResponse(
                    address.getId(),
                    address.getLabel(),
                    address.getRecipientName(),
                    address.getLine1(),
                    address.getLine2(),
                    address.getCity(),
                    address.getState(),
                    address.getPostalCode(),
                    address.getCountry());
        }
    }

    /** Maps an order together with its lines; callers resolve items inside the transaction. */
    public static OrderResponse from(Order order, List<OrderItem> items) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getCouponCode(),
                order.getShippingCost(),
                order.getTax(),
                order.getTotal(),
                order.getCurrency(),
                AddressResponse.from(order.getShippingAddress()),
                AddressResponse.from(order.getBillingAddress()),
                order.getNotes(),
                items.stream().map(OrderItemResponse::from).toList(),
                order.getCreatedAt());
    }

    /** Maps an order whose {@code items} collection is not loaded (list views). */
    public static OrderResponse from(Order order) {
        return from(order, List.of());
    }
}
