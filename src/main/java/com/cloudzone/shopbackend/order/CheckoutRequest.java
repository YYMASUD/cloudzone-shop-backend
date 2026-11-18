package com.cloudzone.shopbackend.order;

import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/orders/checkout}.
 *
 * <p>Addresses may be given either as saved {@code addresses} ids or inline — for each
 * role exactly one of the two must be set, and the billing pair may be omitted entirely
 * (it then mirrors shipping). Currency is not chosen here: it comes from the cart's
 * products, so a mixed-currency cart is rejected rather than silently summed.
 *
 * @param shippingAddressId  a saved address owned by the caller; xor {@code shippingAddress}
 * @param shippingAddress    an inline address to create and use; xor {@code shippingAddressId}
 * @param billingAddressId   saved billing address; omit to reuse the shipping address
 * @param billingAddress     inline billing address; omit to reuse the shipping address
 * @param shippingMethodCode code of an active shipping method (its flat rate prices the order)
 * @param couponCode         optional coupon code, redeemed (counted) as part of checkout
 * @param paymentMethod      gateway to start the payment with; the record is created
 *                           {@code INITIATED} here and driven to a terminal state by
 *                           the gateway flow, never synchronously at checkout
 * @param notes              optional free-text note (DDL: {@code VARCHAR(1000)})
 */
public record CheckoutRequest(
        Long shippingAddressId,
        @Valid CheckoutAddressRequest shippingAddress,
        Long billingAddressId,
        @Valid CheckoutAddressRequest billingAddress,
        @NotBlank @Size(max = 50) String shippingMethodCode,
        @Size(max = 50) String couponCode,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 1000) String notes) {

    /** @return true when a coupon code was supplied (blank counts as absent). */
    public boolean hasCoupon() {
        return couponCode != null && !couponCode.isBlank();
    }
}
