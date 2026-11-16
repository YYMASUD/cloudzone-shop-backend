package com.cloudzone.shopbackend.media;

import com.cloudzone.shopbackend.common.security.AuthPrincipal;
import com.cloudzone.shopbackend.common.security.JwtService;
import com.cloudzone.shopbackend.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Media-asset surface. Everything here is admin-scoped through SecurityConfig's
 * {@code /api/admin/**} gate — browse the index, upload new assets, hard-delete.
 * Serving the bytes to shoppers happens from the object store/CDN, not this API.
 */
@RestController
@RequestMapping("/api/admin/media")
public class MediaController {

    private final MediaService mediaService;
    private final JwtService jwtService;

    public MediaController(MediaService mediaService, JwtService jwtService) {
        this.mediaService = mediaService;
        this.jwtService = jwtService;
    }

    /** Paged asset feed, newest first; oversized {@code size} is clamped. */
    @GetMapping
    public PageResponse<MediaAssetResponse> list(Pageable pageable) {
        return mediaService.list(pageable);
    }

    /** One asset; an unknown id is a 404. */
    @GetMapping("/{id}")
    public MediaAssetResponse get(@PathVariable Long id) {
        return mediaService.get(id);
    }

    /** Multipart upload ({@code file} + optional {@code altText}); 201 with the asset. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MediaAssetResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "altText", required = false) String altText,
            JwtAuthenticationToken authentication) {
        return mediaService.upload(userId(authentication), file, altText);
    }

    /** Removes an asset and its bytes; an unknown id is a 404. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        mediaService.delete(id);
    }

    private long userId(JwtAuthenticationToken authentication) {
        AuthPrincipal principal = jwtService.toPrincipal(authentication.getToken());
        return principal.userId();
    }
}