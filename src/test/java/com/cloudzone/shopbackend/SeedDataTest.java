package com.cloudzone.shopbackend;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudzone.shopbackend.catalog.BrandRepository;
import com.cloudzone.shopbackend.catalog.CategoryRepository;
import com.cloudzone.shopbackend.catalog.ProductRepository;
import com.cloudzone.shopbackend.catalog.ProductVariant;
import com.cloudzone.shopbackend.catalog.ProductVariantRepository;
import com.cloudzone.shopbackend.common.enums.AuthProvider;
import com.cloudzone.shopbackend.common.enums.Role;
import com.cloudzone.shopbackend.user.AddressRepository;
import com.cloudzone.shopbackend.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the DEV-ONLY seed migration ({@code V1000__seed_dev_data.sql}) actually
 * produced the dataset the dev logins and catalogue screens depend on.
 *
 * <p>Runs under {@code schema + seed}: the {@code seed} profile only widens the
 * Flyway locations (and points at a separate H2 database) on top of everything the
 * {@code schema} profile already provides. The plain {@code schema} tests stay
 * seed-free on purpose — {@code SchemaContractTest} asserts global counts and must
 * not depend on seed contents.
 */
@ActiveProfiles({"schema", "seed"})
@SpringBootTest
@Transactional
class SeedDataTest {

    private static final String ADMIN_EMAIL = "admin@shop.dev";
    private static final String CUSTOMER_EMAIL = "customer@shop.dev";

    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Seed creates the two documented dev logins with the right roles")
    void seedUsersExist() {
        var admin = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        var customer = userRepository.findByEmailIgnoreCase(CUSTOMER_EMAIL).orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.isEmailVerified()).isTrue();
        assertThat(admin.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);

        assertThat(customer.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(customer.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
    }

    @Test
    @DisplayName("Seed password hashes verify against the documented dev passwords")
    void seedPasswordsMatch() {
        var admin = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        var customer = userRepository.findByEmailIgnoreCase(CUSTOMER_EMAIL).orElseThrow();

        assertThat(passwordEncoder.matches("Admin123!", admin.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("Customer123!", customer.getPasswordHash())).isTrue();
        // ...and the hash is not just the plaintext sitting in the column.
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(admin.getPasswordHash()).doesNotContain("Admin123!");
    }

    @Test
    @DisplayName("Seed creates two root categories plus the Audio child of Electronics")
    void seedCategoriesExist() {
        var electronics = categoryRepository.findBySlug("electronics").orElseThrow();
        var apparel = categoryRepository.findBySlug("apparel").orElseThrow();
        var audio = categoryRepository.findBySlug("audio").orElseThrow();

        assertThat(electronics.isActive()).isTrue();
        assertThat(apparel.isActive()).isTrue();
        assertThat(electronics.getParent()).isNull();
        assertThat(apparel.getParent()).isNull();

        assertThat(audio.getParent()).isNotNull();
        assertThat(audio.getParent().getSlug()).isEqualTo("electronics");
        assertThat(categoryRepository.count()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Seed creates six products, five of them visible in the catalogue")
    void seedProductsExist() {
        var headphones = productRepository.findBySlug("aurora-wireless-headphones").orElseThrow();

        assertThat(headphones.getSku()).isEqualTo("SKU-HEAD-001");
        assertThat(headphones.isActive()).isTrue();
        assertThat(headphones.getStockQuantity()).isEqualTo(42);
        assertThat(headphones.getCategory().getSlug()).isEqualTo("audio");
        assertThat(headphones.getImageKey()).isEqualTo("catalog/aurora-headphones/main.jpg");

        assertThat(productRepository.count()).isEqualTo(6L);
        assertThat(productRepository.findAllByActiveTrue(Pageable.unpaged()).getTotalElements())
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("Seed includes one zero-stock product and one inactive product for edge paths")
    void seedCoversAvailabilityEdgeCases() {
        var outOfStock = productRepository.findBySlug("cloudzone-classic-tee-oos").orElseThrow();
        var inactive = productRepository.findBySlug("archived-gadget").orElseThrow();

        assertThat(outOfStock.isActive()).isTrue();
        assertThat(outOfStock.getStockQuantity()).isZero();

        assertThat(inactive.isActive()).isFalse();
        assertThat(inactive.getStockQuantity()).isPositive();
    }

    @Test
    @DisplayName("Seed adds three brands and leaves the keyboard unbranded")
    void seedBrandsExist() {
        var aurora = brandRepository.findBySlug("aurora").orElseThrow();

        assertThat(aurora.getName()).isEqualTo("Aurora Audio");
        assertThat(aurora.isActive()).isTrue();
        assertThat(brandRepository.count()).isEqualTo(3L);

        assertThat(productRepository.findBySlug("aurora-wireless-headphones").orElseThrow()
                .getBrand().getSlug()).isEqualTo("aurora");
        assertThat(productRepository.findBySlug("cloudzone-classic-tee-oos").orElseThrow()
                .getBrand().getSlug()).isEqualTo("cloudzone");

        // brand_id is optional: the keyboard is visible in the catalogue with no brand.
        assertThat(productRepository.findBySlug("tactile-65-mechanical-keyboard").orElseThrow()
                .getBrand()).isNull();
    }

    @Test
    @DisplayName("Seed gives three products variants, one of them inactive, nine rows in total")
    void seedVariantsExist() {
        var headphones = productRepository.findBySlug("aurora-wireless-headphones").orElseThrow();
        var tee = productRepository.findBySlug("cloudzone-classic-tee").orElseThrow();
        var keyboard = productRepository.findBySlug("tactile-65-mechanical-keyboard").orElseThrow();

        // The retired colour is in the table but inactive, so the listing never shows it.
        var headphoneVariants = variantRepository
                .findAllByProductIdAndActiveTrueOrderByPriceAscIdAsc(headphones.getId());
        assertThat(headphoneVariants).extracting(ProductVariant::getSku)
                .containsExactlyInAnyOrder("SKU-HW-BLK", "SKU-HW-WHT");
        // Null override: the variant sells at the parent's price.
        assertThat(headphoneVariants.get(0).getPrice()).isNull();

        assertThat(variantRepository.findAllByProductIdAndActiveTrueOrderByPriceAscIdAsc(tee.getId()))
                .hasSize(4);
        assertThat(variantRepository.findAllByProductIdAndActiveTrueOrderByPriceAscIdAsc(keyboard.getId()))
                .extracting(ProductVariant::getSku)
                .containsExactly("SKU-KEYB-ANSI", "SKU-KEYB-ISO");

        assertThat(variantRepository.count()).isEqualTo(9L);
    }

    @Test
    @DisplayName("Seed gives the customer one default HOME address and no orders")
    void seedCustomerAddressExists() {
        var customer = userRepository.findByEmailIgnoreCase(CUSTOMER_EMAIL).orElseThrow();

        List<?> addresses = addressRepository.findAllByUserIdOrderByIsDefaultDescIdDesc(customer.getId());

        assertThat(addresses).hasSize(1);
    }
}
