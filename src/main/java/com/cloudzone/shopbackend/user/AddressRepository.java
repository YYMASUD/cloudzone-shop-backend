package com.cloudzone.shopbackend.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findAllByUserIdOrderByIsDefaultDescIdDesc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);
}
