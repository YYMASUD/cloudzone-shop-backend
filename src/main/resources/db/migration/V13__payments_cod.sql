-- ─────────────────────────────────────────────────────────────────────────────
-- V13__payments_cod.sql — cash-on-delivery as a payment method
--
-- COD is not a gateway: no remote capture ever runs. The payments row still records
-- method='COD' and status=INITIATED at checkout, but the money is collected by the
-- courier and the row is settled on delivery/collection — the same SUCCEEDED terminal
-- a gateway capture produces. Widening the CHECK lets COD rows persist; the enum and
-- the CashOnDeliveryGateway do the behavioural work.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE payments DROP CONSTRAINT chk_payments_method;
ALTER TABLE payments ADD CONSTRAINT chk_payments_method
    CHECK (method IN ('STRIPE','RAZORPAY','PAYPAL','SSLCOMMERZ','COD'));
