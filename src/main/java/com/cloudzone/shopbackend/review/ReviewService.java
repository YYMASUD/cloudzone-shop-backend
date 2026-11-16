package com.cloudzone.shopbackend.review;

import com.cloudzone.shopbackend.catalog.Product;
import com.cloudzone.shopbackend.catalog.ProductRepository;
import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.common.web.PageResponse;
import com.cloudzone.shopbackend.user.User;
import com.cloudzone.shopbackend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product-review logic on the V6 {@code reviews} table: the public per-product feed,
 * the insert-once write path (one review per product per user), owner-scoped edits,
 * and the admin moderation surface.
 *
 * <p>Every customer-facing lookup is owner-scoped, so a foreign review id is
 * indistinguishable from a missing one (404). Admin lookups are deliberately not
 * scoped — moderation needs to see and remove any review.
 */
@Service
public class ReviewService {

    /** Hard ceiling on {@code size}; pages larger than this are clamped, not rejected. */
    private static final int MAX_PAGE_SIZE = 100;

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // ── shopper-facing ────────────────────────────────────────────────────────

    /** One product's reviews, newest first, paged with a hard size cap. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listForProduct(Long productId, Pageable pageable) {
        requireProduct(productId);
        Page<Review> page = reviewRepository.findAllByProductIdOrderByCreatedAtDescIdDesc(
                productId, clamped(pageable));
        return toResponsePage(page);
    }

    /**
     * Writes the caller's review of one active product. Exactly one review per
     * (product, user): a repeat is a 409, not a silent duplicate.
     */
    @Transactional
    public ReviewResponse create(Long userId, Long productId, ReviewCreateRequest request) {
        Product product = requireActiveProduct(productId);
        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "review_already_exists",
                    "User %d already reviewed product %d".formatted(userId, productId));
        }
        Review saved = reviewRepository.save(Review.builder()
                .productId(product.getId())
                .userId(userId)
                .rating(request.rating())
                .title(request.title())
                .body(request.body())
                .authorName(displayName(userRepository.findById(userId).orElse(null)))
                .build());
        return ReviewResponse.from(saved);
    }

    /** Updates an own review; unknown or foreign ids are a 404. Nulls keep old values. */
    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, ReviewUpdateRequest request) {
        Review review = owned(userId, reviewId);
        if (request.rating() != null) {
            review.setRating(request.rating());
        }
        if (request.title() != null) {
            review.setTitle(request.title());
        }
        if (request.body() != null) {
            review.setBody(request.body());
        }
        return ReviewResponse.from(review);
    }

    /** Deletes an own review; an unknown or foreign id is a 404. */
    @Transactional
    public void delete(Long userId, Long reviewId) {
        reviewRepository.delete(owned(userId, reviewId));
    }

    // ── admin-facing ──────────────────────────────────────────────────────────

    /** Every review for moderation, newest first; optionally scoped to one product. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listAll(Long productId, Pageable pageable) {
        Page<Review> page = productId == null
                ? reviewRepository.findAll(clamped(pageable))
                : reviewRepository.findAllByProductIdOrderByCreatedAtDescIdDesc(
                        productId, clamped(pageable));
        return toResponsePage(page);
    }

    /** Removes any review (moderation); an unknown id is a 404. */
    @Transactional
    public void deleteAsAdmin(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "review_not_found", "Unknown review: " + reviewId));
        reviewRepository.delete(review);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private Pageable clamped(Pageable pageable) {
        return PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE),
                NEWEST_FIRST);
    }

    private PageResponse<ReviewResponse> toResponsePage(Page<Review> page) {
        return PageResponse.from(page.map(ReviewResponse::from));
    }

    private Review owned(Long userId, Long reviewId) {
        return reviewRepository.findByIdAndUserId(reviewId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "review_not_found",
                        "No review %d for this user".formatted(reviewId)));
    }

    /** Known product (any state) — reviews of a delisted product stay readable. */
    private Product requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "product_not_found", "Unknown product: " + productId));
    }

    /** Active product — the gate for writing a review, same as wishlist adds. */
    private Product requireActiveProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "product_not_found",
                        "Unknown or inactive product: " + productId));
    }

    private String displayName(User user) {
        if (user == null) {
            return "Shopper";
        }
        String name = (user.getFirstName() + " " + user.getLastName()).trim();
        return name.isBlank() ? "Shopper" : name;
    }
}
