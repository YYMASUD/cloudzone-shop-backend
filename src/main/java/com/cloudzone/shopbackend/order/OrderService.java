package com.cloudzone.shopbackend.order;

import com.cloudzone.shopbackend.cart.Cart;
import com.cloudzone.shopbackend.cart.CartItem;
import com.cloudzone.shopbackend.cart.CartItemRepository;
import com.cloudzone.shopbackend.cart.CartRepository;
import com.cloudzone.shopbackend.catalog.Product;
import com.cloudzone.shopbackend.common.enums.OrderStatus;
import com.cloudzone.shopbackend.common.enums.PaymentStatus;
import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.common.web.PageResponse;
import com.cloudzone.shopbackend.coupon.CouponService;
import com.cloudzone.shopbackend.inventory.HoldStockRequest;
import com.cloudzone.shopbackend.inventory.StockReservationResponse;
import com.cloudzone.shopbackend.inventory.StockReservationService;
import com.cloudzone.shopbackend.notification.NotificationService;
import com.cloudzone.shopbackend.notification.NotificationType;
import com.cloudzone.shopbackend.payment.Payment;
import com.cloudzone.shopbackend.payment.PaymentRepository;
import com.cloudzone.shopbackend.shipping.ShippingMethod;
import com.cloudzone.shopbackend.shipping.ShippingMethodRepository;
import com.cloudzone.shopbackend.user.Address;
import com.cloudzone.shopbackend.user.AddressRepository;
import com.cloudzone.shopbackend.user.User;
import com.cloudzone.shopbackend.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout: turns the caller's cart into an {@link Order} in one atomic transaction.
 *
 * <p>The flow, in order: validate the cart (non-empty, single currency), resolve the
 * shipping method and addresses, hold stock for every line (a 409 here aborts
 * everything — no order, no coupon redemption, no payment row), redeem the optional
 * coupon, persist order + items + an {@code INITIATED} payment, commit the holds to the
 * order, clear the cart, and record an {@code ORDER_PLACED} notification. Because every
 * step joins the surrounding transaction, any failure leaves the database exactly as it
 * was — including the coupon's redemption counter.
 *
 * <p>Money handling: the subtotal comes from the cart's unit-price snapshots (never live
 * catalogue prices), the shipping cost is snapshotted from the method's flat rate, and
 * {@code tax} is zero — no tax rule exists in the system yet, matching the DDL default.
 */
@Service
public class OrderService {

    /** Hard ceiling on {@code size}; pages larger than this are clamped, not rejected. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Hold lifetime for checkout reservations; the expiry sweep reclaims abandoned ones. */
    private static final int HOLD_TTL_MINUTES = 60;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ShippingMethodRepository shippingMethodRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final StockReservationService stockReservationService;
    private final CouponService couponService;
    private final NotificationService notificationService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ShippingMethodRepository shippingMethodRepository,
            AddressRepository addressRepository,
            UserRepository userRepository,
            StockReservationService stockReservationService,
            CouponService couponService,
            NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.shippingMethodRepository = shippingMethodRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.stockReservationService = stockReservationService;
        this.couponService = couponService;
        this.notificationService = notificationService;
    }

    // ── checkout ───────────────────────────────────────────────────────────────

    /**
     * Places an order from the caller's cart.
     *
     * @return the persisted order with its item snapshots
     */
    @Transactional
    public OrderResponse checkout(Long userId, CheckoutRequest request) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> emptyCart());
        List<CartItem> lines = cartItemRepository
                .findAllByCartIdOrderByCreatedAtAscIdAsc(cart.getId());
        if (lines.isEmpty()) {
            throw emptyCart();
        }

        String currency = lines.get(0).getProduct().getCurrency();
        for (CartItem line : lines) {
            if (!line.getProduct().getCurrency().equals(currency)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "mixed_currency",
                        "The cart mixes currencies and cannot be checked out as one order");
            }
        }

        ShippingMethod method = shippingMethodRepository
                .findByCodeIgnoreCase(request.shippingMethodCode())
                .filter(ShippingMethod::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "shipping_method_not_found",
                        "Unknown or inactive shipping method: " + request.shippingMethodCode()));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "user_not_found", "Unknown user: " + userId));

        Address shippingAddress = resolveAddress(userId, request.shippingAddressId(),
                request.shippingAddress(), user);
        Address billingAddress = resolveOptionalBilling(userId, request, user, shippingAddress);

        // Stock holds first: an insufficient-stock 409 must abort before any write that
        // would otherwise need compensating (coupon counter, payment row).
        List<Long> reservationIds = new ArrayList<>();
        for (CartItem line : lines) {
            StockReservationResponse reservation = stockReservationService.hold(userId,
                    new HoldStockRequest(line.getProduct().getId(), null,
                            line.getQuantity(), HOLD_TTL_MINUTES));
            reservationIds.add(reservation.id());
        }

        BigDecimal subtotal = lines.stream()
                .map(line -> line.getUnitPrice()
                        .multiply(BigDecimal.valueOf(line.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = BigDecimal.ZERO;
        if (request.hasCoupon()) {
            discount = couponService
                    .redeemForDiscount(request.couponCode(), subtotal)
                    .discount();
        }

        BigDecimal shippingCost = method.getFlatRate();
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(discount).add(shippingCost).add(tax);

        Order order = orderRepository.save(Order.builder()
                .orderNumber(temporaryOrderNumber())
                .user(user)
                .status(OrderStatus.PENDING)
                .subtotal(subtotal)
                .shippingCost(shippingCost)
                .tax(tax)
                .total(total)
                .discountAmount(discount)
                .couponCode(request.hasCoupon() ? request.couponCode().trim() : null)
                .currency(currency)
                .shippingAddress(shippingAddress)
                .billingAddress(billingAddress)
                .notes(request.notes())
                .build());
        // The id now exists, so the placeholder reference becomes the readable form.
        order.setOrderNumber("ORD-" + Year.now().getValue() + "-"
                + String.format("%06d", order.getId()));

        List<OrderItem> items = new ArrayList<>();
        for (CartItem line : lines) {
            Product product = line.getProduct();
            BigDecimal lineTotal = line.getUnitPrice()
                    .multiply(BigDecimal.valueOf(line.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            items.add(orderItemRepository.save(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .lineTotal(lineTotal)
                    .build()));
        }

        paymentRepository.save(Payment.builder()
                .order(order)
                .method(request.paymentMethod())
                .status(PaymentStatus.INITIATED)
                .amount(total)
                .currency(currency)
                .build());

        // The order now owns the stock: every hold becomes a committed allocation.
        for (Long reservationId : reservationIds) {
            stockReservationService.commit(userId, reservationId, order.getId());
        }

        cartItemRepository.deleteByCartId(cart.getId());

        notificationService.deliver(userId, NotificationType.ORDER_PLACED,
                "Order " + order.getOrderNumber() + " placed",
                "We received your order and will start processing it once payment "
                        + "is confirmed.",
                "/orders/" + order.getOrderNumber());

        return OrderResponse.from(order, items);
    }

    // ── order reads (owner-scoped) ─────────────────────────────────────────────

    /** The caller's orders, newest first, paged with a hard size cap. */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(Long userId, Pageable pageable) {
        Pageable effective = PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE));
        Page<OrderResponse> page = orderRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId, effective)
                .map(OrderResponse::from);
        return PageResponse.from(page);
    }

    /** One of the caller's orders by its human-facing number; foreign numbers are a 404. */
    @Transactional(readOnly = true)
    public OrderResponse get(Long userId, String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "order_not_found",
                        "No order %s for this user".formatted(orderNumber)));
        return OrderResponse.from(order, orderItemRepository.findAllByOrderId(order.getId()));
    }

    // ── lifecycle transitions ─────────────────────────────────────────────────

    /**
     * Cancels one of the caller's orders. Owner-scoped: a foreign number is a 404.
     * Only orders that have not shipped can be cancelled — PENDING releases its
     * (still-ACTIVE) stock holds, while PAID/PROCESSING returns committed stock to
     * the shelf. A monetary reversal for a paid order is the admin refund path.
     */
    @Transactional
    public OrderResponse cancel(Long userId, String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "order_not_found",
                        "No order %s for this user".formatted(orderNumber)));
        applyTransition(order, OrderStatus.CANCELLED);
        return OrderResponse.from(order, orderItemRepository.findAllByOrderId(order.getId()));
    }

    /**
     * Admin-only status move for the fulfilment pipeline (PAID → PROCESSING →
     * SHIPPED → DELIVERED) and admin cancellation. Illegal transitions are a 409.
     */
    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus target) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "order_not_found", "Unknown order: " + orderId));
        applyTransition(order, target);
        return OrderResponse.from(order, orderItemRepository.findAllByOrderId(order.getId()));
    }

    /**
     * The single guard for every order-status change — both the owner cancel path
     * and the admin update path funnel through here, so the legal-transition table
     * and its side-effects (restock, notify) exist exactly once.
     */
    private void applyTransition(Order order, OrderStatus target) {
        OrderStatus from = order.getStatus();
        if (!isLegal(from, target)) {
            throw new ApiException(HttpStatus.CONFLICT, "illegal_status_transition",
                    "Cannot move order %s from %s to %s"
                            .formatted(order.getOrderNumber(), from, target));
        }
        if (target == OrderStatus.CANCELLED) {
            // PENDING holds are still ACTIVE (they subtract from availability) so a
            // release frees them; a paid order's holds are COMMITTED, so the stock
            // itself must go back on the shelf.
            if (from == OrderStatus.PENDING) {
                stockReservationService.releaseAllForOrder(order.getId());
            } else {
                stockReservationService.restockForOrder(order.getId());
            }
        }
        order.setStatus(target);
        orderRepository.save(order);
        dispatchFor(order, target);
    }

    /** The legal state-transition table; {@code REFUNDED} arrives via the payment path only. */
    private static boolean isLegal(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case PENDING -> to == OrderStatus.PAID || to == OrderStatus.CANCELLED;
            case PAID -> to == OrderStatus.PROCESSING
                    || to == OrderStatus.CANCELLED || to == OrderStatus.REFUNDED;
            case PROCESSING -> to == OrderStatus.SHIPPED || to == OrderStatus.CANCELLED;
            case SHIPPED -> to == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED, REFUNDED -> false; // terminal states
        };
    }

    /** Fires the user-facing notification that matches a transition, when one exists. */
    private void dispatchFor(Order order, OrderStatus target) {
        NotificationType type = switch (target) {
            case SHIPPED -> NotificationType.ORDER_SHIPPED;
            case CANCELLED -> NotificationType.ORDER_CANCELLED;
            default -> null;
        };
        if (type == null) {
            return;
        }
        String verb = target == OrderStatus.SHIPPED ? "has shipped" : "was cancelled";
        notificationService.deliver(order.getUser().getId(), type,
                "Order " + order.getOrderNumber() + " " + verb,
                target == OrderStatus.SHIPPED
                        ? "Your order is on its way."
                        : "Your order was cancelled and any stock reserved for it released.",
                "/orders/" + order.getOrderNumber());
    }

    // ── admin-facing ─────────────────────────────────────────────────────────

    /** All orders, newest first, for the admin order desk; oversized {@code size} is clamped. */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listAll(Pageable pageable) {
        Pageable effective = PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE));
        Page<OrderResponse> page = orderRepository
                .findAllByOrderByCreatedAtDesc(effective)
                .map(OrderResponse::from);
        return PageResponse.from(page);
    }

    /** Any order by id, for admin inspection; unknown is a 404. */
    @Transactional(readOnly = true)
    public OrderResponse getById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "order_not_found", "Unknown order: " + orderId));
        return OrderResponse.from(order, orderItemRepository.findAllByOrderId(order.getId()));
    }

    // ── internals ──────────────────────────────────────────────────────────────

    private ApiException emptyCart() {
        return new ApiException(HttpStatus.BAD_REQUEST, "empty_cart",
                "The cart is empty; add items before checking out");
    }

    /**
     * Resolves the shipping address: exactly one of a saved id or an inline body. A saved
     * id must be owned by the caller (foreign ids are a 404, not enumerable); an inline
     * body is persisted as a real {@code Address} row owned by the caller.
     */
    private Address resolveAddress(Long userId, Long addressId,
            CheckoutAddressRequest inline, User user) {
        if (addressId == null && inline == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "address_required",
                    "A shipping address id or inline shipping address is required");
        }
        if (addressId != null && inline != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "address_conflict",
                    "Provide either a shipping address id or an inline address, not both");
        }
        return addressId != null
                ? savedAddress(addressId, userId)
                : createAddress(user, inline);
    }

    /** Billing mirrors shipping when omitted entirely; otherwise the shipping rules apply. */
    private Address resolveOptionalBilling(
            Long userId, CheckoutRequest request, User user, Address shippingAddress) {
        if (request.billingAddressId() == null && request.billingAddress() == null) {
            return shippingAddress;
        }
        return resolveAddress(userId, request.billingAddressId(),
                request.billingAddress(), user);
    }

    private Address savedAddress(Long addressId, Long userId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "address_not_found",
                        "No address %d for this user".formatted(addressId)));
    }

    private Address createAddress(User user, CheckoutAddressRequest inline) {
        return addressRepository.save(Address.builder()
                .user(user)
                .recipientName(inline.recipientName())
                .line1(inline.line1())
                .line2(inline.line2())
                .city(inline.city())
                .state(inline.state())
                .postalCode(inline.postalCode())
                .country(inline.country())
                .isDefault(false)
                .build());
    }

    /**
     * Unique placeholder satisfying {@code order_number VARCHAR(32) NOT NULL UNIQUE}
     * before the id arrives; replaced immediately after the insert flushes with the
     * readable {@code ORD-<year>-<id>} form. A dashed uuid is 40 chars, so the placeholder
     * uses 28 hex digits instead (still ~2^112 of entropy per insert).
     */
    private String temporaryOrderNumber() {
        return "TMP-" + java.util.UUID.randomUUID().toString().replace("-", "")
                .substring(0, 28);
    }
}

