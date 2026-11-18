package com.cloudzone.shopbackend.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findAllByUserIdOrderByIsDefaultDescIdDesc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /**
     * Clears the default flag on every other address of the user. Issued as one bulk
     * update so promoting a new default can never leave two rows flagged if the
     * surrounding transaction rolls back on the second write.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Address a set a.isDefault = false where a.user.id = :userId and a.isDefault = true")
    int clearDefaultFor(@Param("userId") Long userId);
}
