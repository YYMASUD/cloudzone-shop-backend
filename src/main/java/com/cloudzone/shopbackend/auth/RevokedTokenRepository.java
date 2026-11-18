package com.cloudzone.shopbackend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    /** Whether this refresh-token id has been revoked; checked on every exchange. */
    boolean existsByJti(String jti);
}
