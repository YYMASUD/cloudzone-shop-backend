package com.cloudzone.shopbackend.inventory;

import com.cloudzone.shopbackend.catalog.Product;
import com.cloudzone.shopbackend.catalog.ProductRepository;
import com.cloudzone.shopbackend.catalog.ProductVariant;
import com.cloudzone.shopbackend.catalog.ProductVariantRepository;
import com.cloudzone.shopbackend.common.error.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Raises deduplicated low-stock alerts and exposes the admin acknowledgement workflow. */
@Service
public class LowStockAlertService {

    private final LowStockAlertRepository alertRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    public LowStockAlertService(
            LowStockAlertRepository alertRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository) {
        this.alertRepository = alertRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    /**
     * Checks current stock and creates an alert once stock is at/below the threshold.
     * Repeated checks while the alert is open update the observed stock, not add duplicates.
     */
    @Transactional
    public LowStockAlertResponse check(Long productId, Long variantId, int threshold) {
        if (threshold < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_threshold",
                    "threshold must not be negative");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "product_not_found", "Unknown product: " + productId));
        int stock;
        if (variantId == null) {
            stock = product.getStockQuantity();
        } else {
            ProductVariant variant = variantRepository.findById(variantId)
                    .filter(v -> v.getProduct().getId().equals(productId))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "variant_not_found", "Unknown variant: " + variantId));
            stock = variant.getStockQuantity();
        }
        if (stock > threshold) {
            return null;
        }
        LowStockAlert alert = alertRepository
                .findAllByAcknowledgedAtIsNullOrderByCreatedAtDescIdDesc().stream()
                .filter(a -> a.getProductId().equals(productId)
                        && java.util.Objects.equals(a.getVariantId(), variantId))
                .findFirst()
                .orElseGet(() -> LowStockAlert.builder()
                        .productId(productId).variantId(variantId).build());
        alert.setThreshold(threshold);
        alert.setCurrentStock(stock);
        alert.setNotifiedAt(OffsetDateTime.now());
        return LowStockAlertResponse.from(alertRepository.save(alert));
    }

    @Transactional(readOnly = true)
    public List<LowStockAlertResponse> openAlerts() {
        return alertRepository.findAllByAcknowledgedAtIsNullOrderByCreatedAtDescIdDesc()
                .stream().map(LowStockAlertResponse::from).toList();
    }

    @Transactional
    public LowStockAlertResponse acknowledge(Long alertId) {
        LowStockAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "low_stock_alert_not_found", "Unknown low-stock alert: " + alertId));
        if (alert.getAcknowledgedAt() == null) {
            alert.setAcknowledgedAt(OffsetDateTime.now());
        }
        return LowStockAlertResponse.from(alert);
    }
}
