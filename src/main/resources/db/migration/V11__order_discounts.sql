-- ─────────────────────────────────────────────────────────────────────────────
-- V11__order_discounts.sql — coupon discounts on orders
--
-- Checkout can apply a coupon: the discount is snapshotted onto the order so
-- later coupon edits/deletions never rewrite historical totals. The code is
-- denormalized for display and support lookups ("where did I use SAVE10?").
--
-- Default 0 keeps the column NOT NULL for existing rows and lets every order
-- insert omit it when no coupon was used.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE orders ADD COLUMN discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN coupon_code     VARCHAR(50);

-- Support/debug lookups by code; selectivity is decent (most orders have none).
CREATE INDEX idx_orders_coupon ON orders (coupon_code);
