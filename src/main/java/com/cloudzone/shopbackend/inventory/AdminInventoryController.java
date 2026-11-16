package com.cloudzone.shopbackend.inventory;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin view of the inventory ledger. Reachable only with an ADMIN token —
 * {@code SecurityConfig} restricts the whole {@code /api/admin/**} namespace.
 */
@RestController
@RequestMapping("/api/admin/inventory")
public class AdminInventoryController {

    private final LowStockAlertService alertService;
    private final StockReservationService reservationService;

    public AdminInventoryController(
            LowStockAlertService alertService,
            StockReservationService reservationService) {
        this.alertService = alertService;
        this.reservationService = reservationService;
    }

    /** Open (unacknowledged) low-stock alerts, newest first. */
    @GetMapping("/low-stock-alerts")
    public List<LowStockAlertResponse> openAlerts() {
        return alertService.openAlerts();
    }

    /** Acknowledges an alert so a new one can be raised when stock drops again. */
    @PostMapping("/low-stock-alerts/{id}/acknowledge")
    public LowStockAlertResponse acknowledge(@PathVariable Long id) {
        return alertService.acknowledge(id);
    }

    /**
     * Releases every expired ACTIVE hold. Idempotent; callable on a schedule by an
     * external trigger or manually. Returns the count as a plain JSON number.
     */
    @PostMapping("/reservations/expire-sweep")
    public ExpireSweepResponse expireSweep() {
        return new ExpireSweepResponse(reservationService.releaseExpired());
    }

    /** Body of {@link #expireSweep}: how many holds were demoted to RELEASED. */
    public record ExpireSweepResponse(int released) {
    }
}
