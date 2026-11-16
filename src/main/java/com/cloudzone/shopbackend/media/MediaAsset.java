package com.cloudzone.shopbackend.media;

import com.cloudzone.shopbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One uploaded file: an index row pointing at bytes in the backing store. The
 * mapping mirrors {@code V8__media.sql} exactly (Hibernate validates against it
 * under {@code ddl-auto: validate}) — column lengths included.
 *
 * <p>{@code ownerUserId} is a plain {@code BIGINT} like every other module link and
 * is nullable: {@code ON DELETE SET NULL} means assets outlive the account that
 * uploaded them, because a banner must not vanish when an admin leaves.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "media_assets")
public class MediaAsset extends BaseEntity {

    /** Uploader at write time; null once that account is deleted. */
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    /** Client-supplied display name, never used to locate bytes. */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /**
     * Storage address (UUID-based, not client-controlled); unique per
     * {@code uk_media_assets_object_key} so two rows can never share bytes.
     */
    @Column(name = "object_key", nullable = false, updatable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "alt_text")
    private String altText;
}