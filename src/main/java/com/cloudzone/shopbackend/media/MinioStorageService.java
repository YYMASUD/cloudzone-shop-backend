package com.cloudzone.shopbackend.media;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;

/**
 * MinIO-backed storage for production. The client is created eagerly but does not
 * connect until the first call — startup stays green even while the object store is
 * down, and the first upload fails loudly instead.
 */
public class MinioStorageService implements StorageService {

    private final MinioClient client;
    private final String bucket;

    public MinioStorageService(StorageProperties properties) {
        this.client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
        this.bucket = properties.bucket();
    }

    @Override
    public String put(String objectKey, String contentType, InputStream data, long size) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(contentType)
                    // partSize -1 = let the SDK compute it from the known object size;
                    // the 9.x signature is boxed Long, so the literal must be -1L.
                    .stream(data, size, -1L)
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new StorageException("Failed to store object " + objectKey + " in MinIO", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to delete object " + objectKey + " from MinIO", e);
        }
    }
}