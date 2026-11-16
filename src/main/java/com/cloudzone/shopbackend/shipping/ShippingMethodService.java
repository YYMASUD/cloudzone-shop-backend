package com.cloudzone.shopbackend.shipping;

import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.common.web.PageResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shipping method logic: the shopper-facing active-method listing, and the admin CRUD.
 *
 * <p>Flat rates are charged per order; checkout snapshots the rate at purchase time.
 * Codes are unique case-insensitively and immutable after creation.
 */
@Service
public class ShippingMethodService {

    /** Hard ceiling on {@code size}; pages larger than this are clamped, not rejected. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ShippingMethodRepository shippingMethodRepository;

    public ShippingMethodService(ShippingMethodRepository shippingMethodRepository) {
        this.shippingMethodRepository = shippingMethodRepository;
    }

    // ── shopper-facing ────────────────────────────────────────────────────────

    /** Active methods only, cheapest first — the list checkout offers at shipping time. */
    @Transactional(readOnly = true)
    public List<ShippingMethodResponse> listActive() {
        return shippingMethodRepository.findAllByActiveTrueOrderByFlatRateAscIdAsc().stream()
                .map(ShippingMethodResponse::from)
                .toList();
    }

    // ── admin-facing ──────────────────────────────────────────────────────────

    /** Newest-first admin listing, paged with a hard size cap. */
    @Transactional(readOnly = true)
    public PageResponse<ShippingMethodResponse> list(Pageable pageable) {
        Pageable effective = PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE));
        Page<ShippingMethodResponse> page = shippingMethodRepository
                .findAllByOrderByCreatedAtDescIdDesc(effective)
                .map(ShippingMethodResponse::from);
        return PageResponse.from(page);
    }

    /** One method by id; unknown is a 404. */
    @Transactional(readOnly = true)
    public ShippingMethodResponse get(Long id) {
        return ShippingMethodResponse.from(method(id));
    }

    /**
     * Creates a method; a duplicate code (case-insensitive) is a 409.
     * {@code active} defaults to true when omitted.
     */
    @Transactional
    public ShippingMethodResponse create(ShippingMethodCreateRequest request) {
        if (shippingMethodRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "shipping_code_taken",
                    "A shipping method with that code already exists");
        }
        ShippingMethod method = shippingMethodRepository.save(ShippingMethod.builder()
                .code(request.code().trim())
                .name(request.name())
                .description(request.description())
                .flatRate(request.flatRate())
                .estimatedDays(request.estimatedDays())
                .active(request.active() == null || request.active())
                .build());
        return ShippingMethodResponse.from(method);
    }

    /** Applies the non-null fields of the patch; {@code code} is immutable. */
    @Transactional
    public ShippingMethodResponse update(Long id, ShippingMethodUpdateRequest patch) {
        ShippingMethod method = method(id);
        if (patch.name() != null) {
            method.setName(patch.name());
        }
        if (patch.description() != null) {
            method.setDescription(patch.description());
        }
        if (patch.flatRate() != null) {
            method.setFlatRate(patch.flatRate());
        }
        if (patch.estimatedDays() != null) {
            method.setEstimatedDays(patch.estimatedDays());
        }
        if (patch.active() != null) {
            method.setActive(patch.active());
        }
        return ShippingMethodResponse.from(method);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private ShippingMethod method(Long id) {
        return shippingMethodRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "shipping_method_not_found", "Unknown shipping method: " + id));
    }
}