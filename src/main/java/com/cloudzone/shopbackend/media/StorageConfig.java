package com.cloudzone.shopbackend.media;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the {@link StorageService} implementation once at startup from
 * {@code app.storage.mode}. The switch is explicit rather than annotation-driven so
 * there is exactly one place to read to learn which backend a running process uses.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public StorageService storageService(StorageProperties properties) {
        return switch (properties.mode()) {
            case "minio" -> new MinioStorageService(properties);
            case "local" -> new LocalStorageService(properties);
            default -> properties.endpoint() == null || properties.endpoint().isBlank()
                    ? new LocalStorageService(properties)
                    : new MinioStorageService(properties);
        };
    }
}