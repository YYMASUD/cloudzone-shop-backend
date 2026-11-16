package com.cloudzone.shopbackend.media;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    /** Moderation/browse feed, newest first (stable tie-break on id). */
    Page<MediaAsset> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}