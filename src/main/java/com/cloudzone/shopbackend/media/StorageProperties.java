package com.cloudzone.shopbackend.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage settings, bound from the {@code app.storage.*} property tree that
 * already exists in {@code application.yaml}. {@code mode} selects the backend:
 * {@code minio} and {@code local} are explicit; {@code auto} (the default) uses
 * MinIO when an endpoint is configured, otherwise the filesystem.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String mode,
        String localDir) {

    public StorageProperties {
        if (bucket == null || bucket.isBlank()) {
            bucket = "shop-assets";
        }
        if (mode == null || mode.isBlank()) {
            mode = "auto";
        }
        if (localDir == null || localDir.isBlank()) {
            localDir = "target/uploads";
        }
    }
}