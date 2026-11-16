package com.cloudzone.shopbackend.cart;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    /** Lines in insertion order; {@code id ASC} breaks same-timestamp ties. */
    List<CartItem> findAllByCartIdOrderByCreatedAtAscIdAsc(Long cartId);

    void deleteByCartId(Long cartId);
}

