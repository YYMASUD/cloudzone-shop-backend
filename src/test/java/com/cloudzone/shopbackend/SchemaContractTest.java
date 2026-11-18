package com.cloudzone.shopbackend;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudzone.shopbackend.cart.Cart;
import com.cloudzone.shopbackend.cart.CartItem;
import com.cloudzone.shopbackend.cart.CartItemRepository;
import com.cloudzone.shopbackend.cart.CartRepository;
import com.cloudzone.shopbackend.catalog.Brand;
import com.cloudzone.shopbackend.catalog.BrandRepository;
import com.cloudzone.shopbackend.catalog.Category;
import com.cloudzone.shopbackend.catalog.CategoryRepository;
import com.cloudzone.shopbackend.catalog.Product;
import com.cloudzone.shopbackend.catalog.ProductRepository;
import com.cloudzone.shopbackend.catalog.ProductVariant;
import com.cloudzone.shopbackend.catalog.ProductVariantRepository;
import com.cloudzone.shopbackend.common.IdempotencyRecord;
import com.cloudzone.shopbackend.common.IdempotencyRecordRepository;
import com.cloudzone.shopbackend.common.enums.AuthProvider;
import com.cloudzone.shopbackend.common.enums.OrderStatus;
import com.cloudzone.shopbackend.common.enums.PaymentMethod;
import com.cloudzone.shopbackend.common.enums.PaymentStatus;
import com.cloudzone.shopbackend.common.enums.Role;
import com.cloudzone.shopbackend.inventory.LowStockAlert;
import com.cloudzone.shopbackend.inventory.LowStockAlertRepository;
import com.cloudzone.shopbackend.inventory.StockReservation;
import com.cloudzone.shopbackend.inventory.StockReservationRepository;
import com.cloudzone.shopbackend.inventory.StockReservationStatus;
import com.cloudzone.shopbackend.order.Order;
import com.cloudzone.shopbackend.order.OrderItem;
import com.cloudzone.shopbackend.order.OrderItemRepository;
import com.cloudzone.shopbackend.order.OrderRepository;
import com.cloudzone.shopbackend.payment.Payment;
import com.cloudzone.shopbackend.payment.PaymentRepository;
import com.cloudzone.shopbackend.user.Address;
import com.cloudzone.shopbackend.user.AddressRepository;
import com.cloudzone.shopbackend.user.User;
import com.cloudzone.shopbackend.user.UserRepository;
import com.cloudzone.shopbackend.wishlist.WishlistItem;
import com.cloudzone.shopbackend.wishlist.WishlistItemRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves that {@code V1__init.sql} and the JPA entity mappings describe the same
 * schema, and that every derived repository query is valid.
 *
 * <p>Why this test exists: production runs {@code spring.jpa.hibernate.ddl-auto: validate},
 * so any disagreement between the Flyway migration and the entities is a hard startup
 * failure. Reaching the assertions below means Spring already booted, Flyway executed the
 * real migration, and Hibernate validated the mappings against it — so every
 * {@code @Entity} field, table name, column name, length, precision and enum mapping
 * is confirmed consistent.
 *
 * <p>The profile is {@code schema} (see {@code application-schema.yaml}), which points at
 * H2 in PostgreSQL compatibility mode. That keeps the check runnable without Docker while
 * still executing the actual migration file. The MySQL-free round trips also verify that
 * derived queries — including the ones that traverse inherited {@code BaseEntity} fields
 * such as {@code OrderRepository.findAllByUserIdOrderByCreatedAtDesc} — produce the
 * expected results rather than merely parsing.
 */
@ActiveProfiles("schema")
@SpringBootTest
@Transactional
class SchemaContractTest {

    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired private StockReservationRepository stockReservationRepository;
    @Autowired private LowStockAlertRepository lowStockAlertRepository;
    @Autowired private WishlistItemRepository wishlistItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    // ── helpers ──────────────────────────────────────────────────────────────

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
                .firstName("Test")
                .lastName("User")
                .role(Role.CUSTOMER)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .emailVerified(false)
                .build());
    }

    private Category persistCategory(String slug) {
        return categoryRepository.save(Category.builder()
                .name("Category " + slug)
                .slug(slug)
                .description("desc")
                .active(true)
                .build());
    }

    private Product persistProduct(String sku, Category category) {
        return persistProduct(sku, category, "19.99", null);
    }

    private Product persistProduct(String sku, Category category, String price, Brand brand) {
        return productRepository.save(Product.builder()
                .sku(sku)
                .name("Product " + sku)
                .slug(sku.toLowerCase())
                .price(new BigDecimal(price))
                .currency("USD")
                .stockQuantity(10)
                .category(category)
                .brand(brand)
                .active(true)
                .build());
    }

    private Brand persistBrand(String slug) {
        return brandRepository.save(Brand.builder()
                .name("Brand " + slug)
                .slug(slug)
                .description("desc")
                .active(true)
                .build());
    }

    private Order persistOrder(String orderNumber, User user) {
        return orderRepository.save(Order.builder()
                .orderNumber(orderNumber)
                .user(user)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("19.99"))
                .shippingCost(BigDecimal.ZERO)
                .tax(new BigDecimal("1.20"))
                .total(new BigDecimal("21.19"))
                .currency("USD")
                .build());
    }

    // ── schema contract ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Flyway migration and entity mappings agree (ddl-auto=validate passed)")
    void allExpectedTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "select lower(table_name) from information_schema.tables where table_schema = 'public'",
                String.class);

        assertThat(tables)
                .contains("users", "addresses", "categories", "products", "carts",
                        "cart_items", "orders", "order_items", "payments",
                        "idempotency_records", "flyway_schema_history",
                        // added by V2__catalog_brands_variants.sql
                        "brands", "product_variants",
                        // V3__inventory.sql
                        "stock_reservations", "low_stock_alerts",
                        // V4__coupons.sql
                        "coupons",
                        // V5__wishlist.sql
                        "wishlist_items",
                        // V6__reviews.sql
                        "reviews",
                        // V7__notifications.sql
                        "notifications",
                        // V8__media.sql
                        "media_assets",
                        // V9__shipping.sql
                        "shipping_methods", "shipments");
    }

    @Test
    @DisplayName("Money columns are NUMERIC(12,2) — never floating point")
    void moneyColumnsAreExact() {
        // A float column here would silently corrupt totals; assert the declared
        // precision/scale rather than trusting the entity annotations.
        assertThat(numericType("products", "price")).isEqualTo("numeric(12,2)");
        assertThat(numericType("orders", "total")).isEqualTo("numeric(12,2)");
        assertThat(numericType("order_items", "line_total")).isEqualTo("numeric(12,2)");
        assertThat(numericType("payments", "amount")).isEqualTo("numeric(12,2)");
        assertThat(numericType("cart_items", "unit_price")).isEqualTo("numeric(12,2)");
        assertThat(numericType("coupons", "discount_value")).isEqualTo("numeric(12,2)");
        assertThat(numericType("coupons", "min_order_total")).isEqualTo("numeric(12,2)");
        assertThat(numericType("shipping_methods", "flat_rate")).isEqualTo("numeric(12,2)");
    }

    private String numericType(String table, String column) {
        return jdbcTemplate.queryForObject(
                "select lower(data_type) || '(' || numeric_precision || ',' || numeric_scale || ')'"
                        + " from information_schema.columns"
                        + " where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class, table, column);
    }

    private boolean hasColumn(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns"
                        + " where table_schema = 'public' and table_name = ? and column_name = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    // ── repository behaviour ─────────────────────────────────────────────────

    @Test
    @DisplayName("UserRepository: case-insensitive email lookups")
    void userRepositoryLookups() {
        persistUser("Alice@Example.com");

        assertThat(userRepository.findByEmailIgnoreCase("alice@example.com")).isPresent();
        assertThat(userRepository.findByEmailIgnoreCase("ALICE@EXAMPLE.COM")).isPresent();
        assertThat(userRepository.existsByEmailIgnoreCase("AlIcE@eXaMpLe.CoM")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("nobody@example.com")).isFalse();
    }

    @Test
    @DisplayName("AddressRepository: default address sorts first")
    void addressOrderingPutsDefaultFirst() {
        User user = persistUser("addr@example.com");
        addressRepository.save(Address.builder()
                .user(user).recipientName("A").line1("1 St").city("C").country("US")
                .isDefault(false).build());
        addressRepository.save(Address.builder()
                .user(user).recipientName("B").line1("2 St").city("C").country("US")
                .isDefault(true).build());

        List<Address> found = addressRepository.findAllByUserIdOrderByIsDefaultDescIdDesc(user.getId());

        assertThat(found).hasSize(2);
        assertThat(found.get(0).isDefault()).isTrue();
        // Scoped lookup must not leak another user's address.
        assertThat(addressRepository.findByIdAndUserId(found.get(0).getId(), user.getId())).isPresent();
        assertThat(addressRepository.findByIdAndUserId(found.get(0).getId(), user.getId() + 999)).isEmpty();
    }

    @Test
    @DisplayName("ProductRepository: nested category path plus active flag filter")
    void productCategoryQueryTraversesAssociation() {
        Category category = persistCategory("shoes");
        persistProduct("SKU-1", category);
        Product inactive = persistProduct("SKU-2", category);
        inactive.setActive(false);
        productRepository.save(inactive);

        assertThat(productRepository.findAllByActiveTrue(PageRequest.of(0, 10))).hasSize(1);

        // findAllByCategoryIdAndActiveTrue walks category.id — this asserts the
        // implicit join resolves and the active predicate still applies.
        Page<Product> inCategory = productRepository.findAllByCategoryIdAndActiveTrue(
                category.getId(), PageRequest.of(0, 10));
        assertThat(inCategory.getContent()).hasSize(1);
        assertThat(inCategory.getContent().get(0).getSku()).isEqualTo("SKU-1");

        assertThat(productRepository.existsBySku("SKU-1")).isTrue();
        assertThat(productRepository.findBySlug("sku-1")).isPresent();
    }

    @Test
    @DisplayName("V2 schema: brand association persists and stays optional")
    void brandAssociationIsOptionalAndTraverses() {
        Brand brand = persistBrand("branded");

        Category bCat = persistCategory("b-cat");
        Product withBrand = persistProduct("SKU-B1", bCat, "9.99", brand);
        Product unbranded = persistProduct("SKU-B2", bCat, "5.00", null);

        // The FK round-trips through the LAZY ManyToOne, like category does.
        assertThat(productRepository.findById(withBrand.getId()).orElseThrow().getBrand().getSlug())
                .isEqualTo("branded");
        assertThat(productRepository.findById(unbranded.getId()).orElseThrow().getBrand()).isNull();

        assertThat(brandRepository.findBySlug("branded")).isPresent();
        assertThat(brandRepository.existsBySlug("branded")).isTrue();
        assertThat(brandRepository.existsBySlug("absent")).isFalse();
        assertThat(brandRepository.findAllByActiveTrue(Pageable.unpaged()))
                .extracting(Brand::getSlug)
                .containsExactly("branded");
    }

    @Test
    @DisplayName("ProductRepository.search: null filters drop out, set filters AND-combine")
    void searchTreatsNullFiltersAsAbsent() {
        Category cat = persistCategory("search-cat");
        Brand cheap = persistBrand("cheap");
        Brand dear = persistBrand("dear");

        persistProduct("SKU-S1", cat, "10.00", cheap);
        persistProduct("SKU-S2", cat, "30.00", dear);
        Product pricey = persistProduct("SKU-S3", cat, "90.00", null);
        pricey.setName("Completely unrelated gizmo");
        productRepository.save(pricey);

        // No filters: every active product of the (transaction-local) table.
        Page<Product> unfiltered = productRepository.search(
                null, null, null, null, null, PageRequest.of(0, 10));
        assertThat(unfiltered.getContent())
                .extracting(Product::getSku)
                .contains("SKU-S1", "SKU-S2", "SKU-S3");

        // A single brand filter.
        assertThat(productRepository.search(null, cheap.getId(), null, null, null,
                        PageRequest.of(0, 10)).getContent())
                .extracting(Product::getSku)
                .containsExactly("SKU-S1");

        // Category AND brand AND price window AND free text — all at once.
        assertThat(productRepository.search(cat.getId(), cheap.getId(),
                        new BigDecimal("5.00"), new BigDecimal("20.00"), "Product",
                        PageRequest.of(0, 10)).getContent())
                .extracting(Product::getSku)
                .containsExactly("SKU-S1");

        // Price window excludes on both ends; text must match name or description.
        assertThat(productRepository.search(null, null,
                        new BigDecimal("25.00"), null, null, PageRequest.of(0, 10)).getContent())
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder("SKU-S2", "SKU-S3");
        assertThat(productRepository.search(null, null, null, null, "unrelated",
                        PageRequest.of(0, 10)).getContent())
                .extracting(Product::getSku)
                .containsExactly("SKU-S3");
    }

    @Test
    @DisplayName("ProductRepository.search: LEFT JOINs keep category-less and unbranded rows")
    void searchDoesNotDropRowsWithoutCategoryOrBrand() {
        // Neither category nor brand — an inner join on either path would hide it.
        Product orphan = persistProduct("SKU-O1", null, "12.00", null);
        orphan.setName("Orphan widget");
        productRepository.save(orphan);

        Page<Product> unfiltered = productRepository.search(
                null, null, null, null, null, PageRequest.of(0, 10));
        assertThat(unfiltered.getContent()).extracting(Product::getSku).contains("SKU-O1");

        // Free text still reaches it: the WHERE clause is on p's own columns.
        assertThat(productRepository.search(null, null, null, null, "orphan",
                        PageRequest.of(0, 10)).getContent())
                .extracting(Product::getSku)
                .containsExactly("SKU-O1");
    }

    @Test
    @DisplayName("ProductVariantRepository: active-only, cheapest first, id tie-break")
    void variantListingFiltersAndOrders() {
        Category cat = persistCategory("variant-cat");
        Product product = persistProduct("SKU-V1", cat);
        Product other = persistProduct("SKU-V2", cat);

        variantRepository.save(ProductVariant.builder()
                .product(product).sku("SKU-V1-A").name("A").options("Opt: A")
                .price(new BigDecimal("20.00")).currency("USD").stockQuantity(1).active(true).build());
        variantRepository.save(ProductVariant.builder()
                .product(product).sku("SKU-V1-B").name("B").options("Opt: B")
                .price(new BigDecimal("10.00")).currency("USD").stockQuantity(2).active(true).build());
        variantRepository.save(ProductVariant.builder()
                .product(product).sku("SKU-V1-C").name("C (inactive)").options("Opt: C")
                .price(new BigDecimal("1.00")).currency("USD").stockQuantity(3).active(false).build());
        variantRepository.save(ProductVariant.builder()
                .product(other).sku("SKU-V2-A").name("Other product's variant")
                .price(null).currency("USD").stockQuantity(4).active(true).build());

        List<ProductVariant> variants = variantRepository
                .findAllByProductIdAndActiveTrueOrderByPriceAscIdAsc(product.getId());

        // The inactive row and the other product's row never appear; explicit prices order ascending.
        assertThat(variants).extracting(ProductVariant::getSku)
                .containsExactly("SKU-V1-B", "SKU-V1-A");
    }

    @Test
    @DisplayName("CartRepository: one cart per user")
    void cartIsRetrievableByUser() {
        User user = persistUser("cart@example.com");
        cartRepository.save(Cart.builder().user(user).build());

        assertThat(cartRepository.findByUserId(user.getId())).isPresent();
        assertThat(cartRepository.findByUserId(user.getId() + 999)).isEmpty();
    }

    @Test
    @DisplayName("CartItemRepository: derived delete removes only the target cart's rows")
    void cartItemDeleteIsScoped() {
        User userA = persistUser("a@example.com");
        User userB = persistUser("b@example.com");
        Category category = persistCategory("cat");
        Product product = persistProduct("SKU-D", category);

        Cart cartA = cartRepository.save(Cart.builder().user(userA).build());
        Cart cartB = cartRepository.save(Cart.builder().user(userB).build());

        cartItemRepository.save(CartItem.builder()
                .cart(cartA).product(product).quantity(1).unitPrice(new BigDecimal("9.99")).build());
        cartItemRepository.save(CartItem.builder()
                .cart(cartB).product(product).quantity(2).unitPrice(new BigDecimal("9.99")).build());

        // deleteByCartId is a derived delete: it loads then removes each entity,
        // which is why callers must supply a transaction (this class is @Transactional).
        cartItemRepository.deleteByCartId(cartA.getId());
        cartItemRepository.flush();

        assertThat(cartItemRepository.findByCartIdAndProductId(cartA.getId(), product.getId())).isEmpty();
        assertThat(cartItemRepository.findByCartIdAndProductId(cartB.getId(), product.getId())).isPresent();
    }

    @Test
    @DisplayName("OrderRepository: sort on inherited createdAt resolves")
    void orderPagingSortsOnInheritedCreatedAt() {
        User user = persistUser("orders@example.com");
        persistOrder("ORD-1", user);
        persistOrder("ORD-2", user);

        // Ordering by createdAt lives on BaseEntity; a mapping problem here would
        // fail at startup, not at assertion time.
        Page<Order> page = orderRepository.findAllByUserIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(orderRepository.findByOrderNumber("ORD-1")).isPresent();
        assertThat(orderRepository.findByOrderNumber("NOPE")).isEmpty();
    }

    private Payment persistPayment(String ref, Order order, PaymentStatus status) {
        return paymentRepository.save(Payment.builder()
                .order(order).method(PaymentMethod.STRIPE).status(status)
                .amount(new BigDecimal("21.19")).currency("USD")
                .gatewayReference(ref).failureReason("card declined").build());
    }

    @Test
    @DisplayName("PaymentRepository: latest attempt is chosen by createdAt")
    void paymentReturnsLatestAttempt() {
        User user = persistUser("pay@example.com");
        Order order = persistOrder("ORD-P", user);

        persistPayment("gw-1", order, PaymentStatus.FAILED);
        Payment second = persistPayment("gw-2", order, PaymentStatus.SUCCEEDED);

        assertThat(paymentRepository.findByGatewayReference("gw-1")).isPresent();

        // The retry workflow depends on picking the newest attempt, not the lowest id.
        // id DESC breaks created_at ties (same-transaction rows share a timestamp).
        assertThat(paymentRepository.findFirstByOrderIdOrderByCreatedAtDescIdDesc(order.getId()))
                .isPresent()
                .get()
                .extracting(Payment::getStatus)
                .isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("OrderItemRepository: lines are fetched by order")
    void orderItemsBelongToOrder() {
        User user = persistUser("lines@example.com");
        Category category = persistCategory("lines");
        Product product = persistProduct("SKU-L", category);
        Order order = persistOrder("ORD-L", user);

        orderItemRepository.save(OrderItem.builder()
                .order(order).product(product)
                .productName(product.getName()).productSku(product.getSku())
                .quantity(2).unitPrice(new BigDecimal("19.99"))
                .lineTotal(new BigDecimal("39.98")).build());

        assertThat(orderItemRepository.findAllByOrderId(order.getId())).hasSize(1);
    }

    @Test
    @DisplayName("IdempotencyRecordRepository: expiry sweep deletes only expired rows")
    void idempotencyExpirySweep() {
        idempotencyRecordRepository.save(IdempotencyRecord.builder()
                .idempotencyKey("expired").requestFingerprint("fp")
                .responseStatus(200).responseBody("{}")
                .expiresAt(OffsetDateTime.now().minusHours(1)).build());
        idempotencyRecordRepository.save(IdempotencyRecord.builder()
                .idempotencyKey("live").requestFingerprint("fp")
                .responseStatus(200).responseBody("{}")
                .expiresAt(OffsetDateTime.now().plusHours(1)).build());

        idempotencyRecordRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
        idempotencyRecordRepository.flush();

        assertThat(idempotencyRecordRepository.findByIdempotencyKey("expired")).isEmpty();
        assertThat(idempotencyRecordRepository.findByIdempotencyKey("live")).isPresent();
    }

    @Test
    @DisplayName("Enums persist as readable strings, not ordinals")
    void enumsAreStoredAsStrings() {
        User user = persistUser("enum@example.com");
        Order order = persistOrder("ORD-E", user);
        paymentRepository.save(Payment.builder()
                .order(order).method(PaymentMethod.RAZORPAY).status(PaymentStatus.PENDING)
                .amount(new BigDecimal("5.00")).currency("USD").build());

        // @Enumerated(EnumType.STRING) — a stray ordinal would show up as a digit.
        assertThat(jdbcTemplate.queryForObject(
                "select role from users where email = ?", String.class, "enum@example.com"))
                .isEqualTo("CUSTOMER");
        assertThat(jdbcTemplate.queryForObject(
                "select status from orders where order_number = ?", String.class, "ORD-E"))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "select method from payments where order_id = ?", String.class, order.getId()))
                .isEqualTo("RAZORPAY");
    }

    @Test
    @DisplayName("V3-V11 commerce migrations expose the entity-mapped columns")
    void commerceMigrationColumnsExist() {
        assertThat(hasColumn("stock_reservations", "product_id")).isTrue();
        assertThat(hasColumn("stock_reservations", "user_id")).isTrue();
        assertThat(hasColumn("stock_reservations", "variant_id")).isTrue();
        assertThat(hasColumn("stock_reservations", "status")).isTrue();
        assertThat(hasColumn("low_stock_alerts", "acknowledged_at")).isTrue();
        assertThat(hasColumn("low_stock_alerts", "current_stock")).isTrue();
        assertThat(hasColumn("coupons", "discount_type")).isTrue();
        assertThat(hasColumn("coupons", "discount_value")).isTrue();
        assertThat(hasColumn("coupons", "times_redeemed")).isTrue();
        assertThat(hasColumn("wishlist_items", "user_id")).isTrue();
        assertThat(hasColumn("wishlist_items", "product_id")).isTrue();
        assertThat(hasColumn("reviews", "rating")).isTrue();
        assertThat(hasColumn("reviews", "body")).isTrue();
        assertThat(hasColumn("notifications", "read_at")).isTrue();
        assertThat(hasColumn("notifications", "type")).isTrue();
        assertThat(hasColumn("media_assets", "owner_user_id")).isTrue();
        assertThat(hasColumn("media_assets", "object_key")).isTrue();
        assertThat(hasColumn("shipping_methods", "flat_rate")).isTrue();
        assertThat(hasColumn("shipping_methods", "estimated_days")).isTrue();
        assertThat(hasColumn("shipments", "order_id")).isTrue();
        assertThat(hasColumn("shipments", "tracking_number")).isTrue();
        // V11: the coupon snapshot on orders (see V11__order_discounts.sql).
        assertThat(hasColumn("orders", "discount_amount")).isTrue();
        assertThat(hasColumn("orders", "coupon_code")).isTrue();
        assertThat(numericType("orders", "discount_amount")).isEqualTo("numeric(12,2)");
    }

    // ── V3: inventory ledger ─────────────────────────────────────────────────

    @Test
    @DisplayName("StockReservationRepository: availability sums only ACTIVE unvarianted holds")
    void reservationLedgerDrivesAvailability() {
        User user = persistUser("ledger@example.com");
        Category category = persistCategory("ledger-cat");
        Product product = persistProduct("SKU-R1", category);
        Product other = persistProduct("SKU-R2", category);

        stockReservationRepository.save(StockReservation.builder()
                .userId(user.getId()).productId(product.getId()).quantity(3)
                .status(StockReservationStatus.ACTIVE)
                .expiresAt(OffsetDateTime.now().plusMinutes(10)).build());
        stockReservationRepository.save(StockReservation.builder()
                .userId(user.getId()).productId(product.getId()).quantity(2)
                .status(StockReservationStatus.ACTIVE)
                .expiresAt(OffsetDateTime.now().plusMinutes(10)).build());
        // A released hold must stop counting against availability.
        stockReservationRepository.save(StockReservation.builder()
                .userId(user.getId()).productId(product.getId()).quantity(5)
                .status(StockReservationStatus.RELEASED)
                .expiresAt(OffsetDateTime.now().plusMinutes(10)).build());
        // Another product's holds must not bleed into this product's math.
        stockReservationRepository.save(StockReservation.builder()
                .userId(user.getId()).productId(other.getId()).quantity(7)
                .status(StockReservationStatus.ACTIVE)
                .expiresAt(OffsetDateTime.now().plusMinutes(10)).build());

        assertThat(stockReservationRepository.sumActiveForProduct(
                StockReservationStatus.ACTIVE, product.getId())).isEqualTo(5L);
        assertThat(stockReservationRepository.sumActiveForProduct(
                StockReservationStatus.ACTIVE, other.getId())).isEqualTo(7L);

        // Newest first with id tie-break: same-transaction rows share a timestamp.
        assertThat(stockReservationRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(user.getId()))
                .extracting(StockReservation::getQuantity)
                .containsExactly(7, 5, 2, 3);
    }

    @Test
    @DisplayName("StockReservationRepository: expiry sweep selects only expired ACTIVE holds")
    void expirySweepSelectsExpiredActiveHolds() {
        User user = persistUser("expire@example.com");
        Product product = persistProduct("SKU-R3", persistCategory("expire-cat"));

        stockReservationRepository.save(StockReservation.builder()
                .userId(user.getId()).productId(product.getId()).quantity(1)
                .status(StockReservationStatus.ACTIVE)
                .expiresAt(OffsetDateTime.now().minusHours(1)).build());
        stockReservationRepository.save(StockReservation.builder()
                .userId(user.getId()).productId(product.getId()).quantity(2)
                .status(StockReservationStatus.ACTIVE)
                .expiresAt(OffsetDateTime.now().plusHours(1)).build());
        stockReservationRepository.save(StockReservation.builder()
                .userId(user.getId()).productId(product.getId()).quantity(4)
                .status(StockReservationStatus.RELEASED)
                .expiresAt(OffsetDateTime.now().minusHours(1)).build());

        assertThat(stockReservationRepository.findAllByStatusAndExpiresAtBefore(
                StockReservationStatus.ACTIVE, OffsetDateTime.now()))
                .extracting(StockReservation::getQuantity)
                .containsExactly(1);
    }

    @Test
    @DisplayName("LowStockAlertRepository: open alerts sort newest first, acknowledged drop out")
    void lowStockAlertQueries() {
        Product product = persistProduct("SKU-A1", persistCategory("alert-cat"));
        Product other = persistProduct("SKU-A2", persistCategory("alert-cat2"));
        ProductVariant variant = variantRepository.save(ProductVariant.builder()
                .product(other).sku("SKU-A2-V1").name("V1").options("Opt: V1")
                .currency("USD").stockQuantity(0).active(true).build());
        // A second variant so the acknowledged-only case can be asserted in isolation.
        ProductVariant retiredVariant = variantRepository.save(ProductVariant.builder()
                .product(other).sku("SKU-A2-V2").name("V2").options("Opt: V2")
                .currency("USD").stockQuantity(0).active(true).build());

        lowStockAlertRepository.save(LowStockAlert.builder()
                .productId(product.getId()).threshold(5).currentStock(3)
                .notifiedAt(OffsetDateTime.now()).build());
        lowStockAlertRepository.save(LowStockAlert.builder()
                .productId(other.getId()).variantId(variant.getId()).threshold(5).currentStock(2)
                .notifiedAt(OffsetDateTime.now()).build());
        LowStockAlert acknowledged = lowStockAlertRepository.save(LowStockAlert.builder()
                .productId(other.getId()).variantId(retiredVariant.getId()).threshold(5).currentStock(1)
                .notifiedAt(OffsetDateTime.now()).build());
        acknowledged.setAcknowledgedAt(OffsetDateTime.now());
        lowStockAlertRepository.save(acknowledged);

        assertThat(lowStockAlertRepository
                .findAllByAcknowledgedAtIsNullOrderByCreatedAtDescIdDesc())
                .extracting(LowStockAlert::getProductId)
                .containsExactly(other.getId(), product.getId());

        assertThat(lowStockAlertRepository.existsByProductIdAndVariantIdAndAcknowledgedAtIsNull(
                other.getId(), variant.getId())).isTrue();    // an open alert exists
        assertThat(lowStockAlertRepository.existsByProductIdAndVariantIdAndAcknowledgedAtIsNull(
                other.getId(), retiredVariant.getId())).isFalse();   // acknowledged
        assertThat(lowStockAlertRepository.existsByProductIdAndVariantIdAndAcknowledgedAtIsNull(
                other.getId(), 9999L)).isFalse();             // never raised
    }

    // ── V5: wishlist ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("WishlistItemRepository: owner-scoped listing, existence, derived delete")
    void wishlistQueriesAreOwnerScoped() {
        User user = persistUser("wish@example.com");
        User stranger = persistUser("wish-other@example.com");
        Product product = persistProduct("SKU-W1", persistCategory("wish-cat"));
        Product other = persistProduct("SKU-W2", persistCategory("wish-cat2"));

        wishlistItemRepository.save(WishlistItem.builder()
                .userId(user.getId()).productId(product.getId()).build());
        wishlistItemRepository.save(WishlistItem.builder()
                .userId(user.getId()).productId(other.getId()).build());
        wishlistItemRepository.save(WishlistItem.builder()
                .userId(stranger.getId()).productId(product.getId()).build());

        assertThat(wishlistItemRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(user.getId()))
                .extracting(WishlistItem::getProductId)
                .containsExactly(other.getId(), product.getId());

        assertThat(wishlistItemRepository.existsByUserIdAndProductId(
                user.getId(), product.getId())).isTrue();
        // The stranger saved the same product — but for a different user, so it is absent here.
        assertThat(wishlistItemRepository.existsByUserIdAndProductId(
                user.getId() + 999, product.getId())).isFalse();

        // deleteByUserIdAndProductId removes only that user's bookmark.
        wishlistItemRepository.deleteByUserIdAndProductId(user.getId(), product.getId());
        wishlistItemRepository.flush();
        assertThat(wishlistItemRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(user.getId()))
                .extracting(WishlistItem::getProductId)
                .containsExactly(other.getId());
        // The stranger's row is untouched.
        assertThat(wishlistItemRepository.existsByUserIdAndProductId(
                stranger.getId(), product.getId())).isTrue();
    }
}
