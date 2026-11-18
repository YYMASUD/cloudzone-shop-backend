-- ─────────────────────────────────────────────────────────────────────────────
-- V12__payments_provider.sql — provider capture/refund bookkeeping on payments
--
-- Checkout already writes one INITIATED payment row per order (V1). The provider
-- layer needs a place to record what the gateway returned after confirmation and
-- what came back on refund, so reconciliation and support can answer "did the
-- money actually move?" without opening the raw gateway_payload dump.
--
-- All columns are nullable with no default: existing INITIATED rows stay valid,
-- and a payment that was never confirmed simply has no receipt or timestamps.
-- refund_amount defaults to 0 rather than NULL so "nothing refunded yet" is a
-- value, not a tri-state.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE payments ADD COLUMN receipt_number VARCHAR(255);
ALTER TABLE payments ADD COLUMN paid_at        TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN refunded_at    TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN refund_amount  NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN refund_reason  VARCHAR(500);

-- Refund bookkeeping must never claim more than the captured amount.
ALTER TABLE payments ADD CONSTRAINT chk_payments_refund_amount
    CHECK (refund_amount >= 0 AND refund_amount <= amount);

-- Support/finance lookups by gateway receipt (e.g. Stripe charge id, Razorpay
-- payment id). Nullable column → the index only carries rows that have one.
CREATE INDEX idx_payments_receipt ON payments (receipt_number);
