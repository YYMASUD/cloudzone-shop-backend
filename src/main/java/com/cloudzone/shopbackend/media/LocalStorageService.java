package com.cloudzone.shopbackend.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Filesystem-backed storage used for dev and tests: bytes land under
 * {@code app.storage.local-dir}, preserving the object key as a relative path.
 *
 * <p>{@link #resolveSafely} normalises every key and refuses anything that escapes the
 * root directory, so a hostile key such as {@code ../../etc/passwd} fails closed.
 */
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(StorageProperties properties) {
        this.root = Path.of(properties.localDir()).toAbsolutePath().normalize();
    }

    @Override
    public String put(String objectKey, String contentType, InputStream data, long size) {
        Path target = resolveSafely(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Failed to store object " + objectKey, e);
        }
        return objectKey;
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolveSafely(objectKey));
        } catch (IOException e) {
            throw new StorageException("Failed to delete object " + objectKey, e);
        } catch (UncheckedIOException e) {
            throw new StorageException("Failed to delete object " + objectKey, e);
        }
    }

    private Path resolveSafely(String objectKey) {
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException("Illegal object key: " + objectKey);
        }
        return resolved;
    }
}