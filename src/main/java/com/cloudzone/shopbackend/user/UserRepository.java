package com.cloudzone.shopbackend.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Count of enabled (non-disabled) accounts, for the admin dashboard. */
    long countByEnabledTrue();

    /** Count of accounts in a role, for the admin dashboard's role breakdown. */
    long countByRole(com.cloudzone.shopbackend.common.enums.Role role);
}
