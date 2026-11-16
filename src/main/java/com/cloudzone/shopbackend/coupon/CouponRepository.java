package com.cloudzone.shopbackend.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /** Case-insensitive code lookup — codes are matched regardless of client casing. */
    Optional<Coupon> findByCodeIgnoreCase(String code);

    /** Case-insensitive uniqueness probe for the admin create path. */
    boolean existsByCodeIgnoreCase(String code);

    /** Admin listing: everything, newest first. */
    Page<Coupon> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}