package com.cloudzone.shopbackend.shipping;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, Long> {

    /** Case-insensitive uniqueness probe for the admin create path. */
    boolean existsByCodeIgnoreCase(String code);

    /** Case-insensitive code lookup — checkout resolves methods by their code. */
    Optional<ShippingMethod> findByCodeIgnoreCase(String code);

    /** Shopper listing: active methods only, cheapest first with a stable id tiebreak. */
    List<ShippingMethod> findAllByActiveTrueOrderByFlatRateAscIdAsc();

    /** Admin listing: everything (inactive included), newest first. */
    Page<ShippingMethod> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}