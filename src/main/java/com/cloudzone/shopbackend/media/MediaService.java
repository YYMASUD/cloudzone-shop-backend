package com.cloudzone.shopbackend.media;

import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.common.web.PageResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Media-asset logic on the V8 {@code media_assets} table: a paged browse/moderation
 * feed plus an upload pipeline that validates, stores bytes, then writes the index row.
 *
 * <p>Uploads are admin-only (SecurityConfig's {@code /api/admin/**} gate); the service
 * still attributes ownership from the verified JWT so {@code media_assets.owner_user_id}
 * records who uploaded what. Validation happens before any byte is stored, so a bad
 * request never touches the backing store.
 */
@Service
public class MediaService {

    /** Hard upload ceiling — enforced before storage, so oversized bytes are dropped. */
    static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    private static final int MAX_ALT_LENGTH = 255;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    /** What the product surface may embed; anything else is a 415 before upload. */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf");

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp",
            "image/gif", ".gif", "application/pdf", ".pdf");

    private final MediaAssetRepository repository;
    private final StorageService storage;

    public MediaService(MediaAssetRepository repository, StorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    // ── read surface ─────────────────────────────────────────────────────────

    /** Paged browse feed, newest first; oversized {@code size} is clamped. */
    @Transactional(readOnly = true)
    public PageResponse<MediaAssetResponse> list(Pageable pageable) {
        Page<MediaAsset> page =
                repository.findAllByOrderByCreatedAtDescIdDesc(clamped(pageable));
        return PageResponse.from(page.map(MediaAssetResponse::from));
    }

    /** One asset; an unknown id is a 404. */
    @Transactional(readOnly = true)
    public MediaAssetResponse get(Long id) {
        return MediaAssetResponse.from(existing(id));
    }

    // ── write surface ────────────────────────────────────────────────────────

    /**
     * Stores an upload under a server-generated key and indexes it. The key is
     * UUID-based so client filenames never touch the storage layout; the display
     * name is kept (sanitised) purely for admins.
     */
    @Transactional
    public MediaAssetResponse upload(Long ownerUserId, MultipartFile file, String altText) {
        if (altText != null && altText.length() > MAX_ALT_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_failed",
                    "altText must be at most 255 characters");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "media_empty",
                    "Uploaded file is empty");
        }
        String contentType = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type",
                    "Unsupported content type: " + contentType);
        }
        long size = file.getSize();
        if (size > MAX_SIZE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "media_too_large",
                    "File exceeds the 5 MB limit");
        }
        String objectKey = "u%d/%s%s".formatted(
                ownerUserId, UUID.randomUUID(), EXTENSIONS.get(contentType));
        try (InputStream in = file.getInputStream()) {
            storage.put(objectKey, contentType, in, size);
        } catch (IOException e) {
            throw new StorageException("Failed to read uploaded bytes", e);
        }
        try {
            MediaAsset saved = repository.save(MediaAsset.builder()
                    .ownerUserId(ownerUserId)
                    .fileName(sanitizeName(file.getOriginalFilename()))
                    .objectKey(objectKey)
                    .contentType(contentType)
                    .sizeBytes(size)
                    .altText(altText == null || altText.isBlank() ? null : altText)
                    .build());
            return MediaAssetResponse.from(saved);
        } catch (RuntimeException e) {
            // Row write failed — never leave an orphaned object behind.
            storage.delete(objectKey);
            throw e;
        }
    }

    /**
     * Deletes the index row first and the bytes second: if the storage call fails the
     * transaction rolls the row back, so the worst case is an unreferenced object, never
     * a row whose bytes are gone.
     */
    @Transactional
    public void delete(Long id) {
        MediaAsset asset = existing(id);
        repository.delete(asset);
        storage.delete(asset.getObjectKey());
    }

    // ── internals ────────────────────────────────────────────────────────────

    private MediaAsset existing(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "media_not_found", "Unknown media asset: " + id));
    }

    private Pageable clamped(Pageable pageable) {
        return PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE),
                NEWEST_FIRST);
    }

    /** Strips path components and caps the length so the value fits the column. */
    private static String sanitizeName(String original) {
        if (original == null || original.isBlank()) {
            return "upload";
        }
        String name = original.substring(
                Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}