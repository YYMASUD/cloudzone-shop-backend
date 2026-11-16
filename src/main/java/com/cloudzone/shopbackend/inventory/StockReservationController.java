package com.cloudzone.shopbackend.inventory;

import com.cloudzone.shopbackend.common.security.AuthPrincipal;
import com.cloudzone.shopbackend.common.security.JwtService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The shopper's stock-reservation surface: place a hold, release it, commit it to an
 * order, or review the ledger's own history. Owner-scoped — a hold id belonging to
 * another user is indistinguishable from a missing one (404), so ids are not enumerable.
 */
@RestController
@RequestMapping("/api/inventory/reservations")
public class StockReservationController {

    private final StockReservationService reservationService;
    private final JwtService jwtService;

    public StockReservationController(
            StockReservationService reservationService, JwtService jwtService) {
        this.reservationService = reservationService;
        this.jwtService = jwtService;
    }

    /** Places a hold if the units are available; 409 {@code insufficient_stock} otherwise. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockReservationResponse hold(
            @Valid @RequestBody HoldStockRequest request,
            JwtAuthenticationToken authentication) {
        return reservationService.hold(userId(authentication), request);
    }

    /** The caller's own reservations, newest first. */
    @GetMapping
    public List<StockReservationResponse> history(JwtAuthenticationToken authentication) {
        return reservationService.history(userId(authentication));
    }

    /** Gives a hold back; only the owner and only while ACTIVE. */
    @PostMapping("/{id}/release")
    public StockReservationResponse release(
            @PathVariable Long id,
            JwtAuthenticationToken authentication) {
        return reservationService.release(userId(authentication), id);
    }

    /** Commits a hold to an order — the checkout flow calls this once the order exists. */
    @PostMapping("/{id}/commit")
    public StockReservationResponse commit(
            @PathVariable Long id,
            @Valid @RequestBody CommitReservationRequest request,
            JwtAuthenticationToken authentication) {
        return reservationService.commit(userId(authentication), id, request.orderId());
    }

    private long userId(JwtAuthenticationToken authentication) {
        AuthPrincipal principal = jwtService.toPrincipal(authentication.getToken());
        return principal.userId();
    }
}
