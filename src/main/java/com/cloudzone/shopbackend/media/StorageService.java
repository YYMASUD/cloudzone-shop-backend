package com.cloudzone.shopbackend.media;

import java.io.InputStream;

/**
 * The seam between the media module and wherever bytes actually live (MinIO in
 * production, the local filesystem in tests/dev). The {@code media_assets} row
 * is the index; this interface owns the bytes and is keyed by {@code object_key}.
 */
public interface StorageService {

    /**
     * Stores {@code data} under {@code objectKey}, overwriting anything already there.
     *
     * @return the object key actually stored (same value as the argument)
     */
    String put(String objectKey, String contentType, InputStream data, long size);

    /** Best-effort removal; deleting an absent object is not an error. */
    void delete(String objectKey);
}