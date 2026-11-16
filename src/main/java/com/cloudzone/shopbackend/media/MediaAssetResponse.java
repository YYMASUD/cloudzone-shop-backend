package com.cloudzone.shopbackend.media;

import java.time.OffsetDateTime;

/**
 * One media asset as the API returns it. Admin-only surface, so {@code ownerUserId}
 * is exposed here (unlike shopper-facing DTOs, which hide account ids).
 */
public record MediaAssetResponse(
        Long id,
        Long ownerUserId,
        String fileName,
        String objectKey,
        String contentType,
        Long sizeBytes,
        String altText,
        OffsetDateTime createdAt) {

    public static MediaAssetResponse from(MediaAsset asset) {
        return new MediaAssetResponse(
                asset.getId(),
                asset.getOwnerUserId(),
                asset.getFileName(),
                asset.getObjectKey(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getAltText(),
                asset.getCreatedAt());
    }
}