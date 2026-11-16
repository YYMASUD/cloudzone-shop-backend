-- ─────────────────────────────────────────────────────────────────────────────
-- V1000__seed_dev_data.sql  —  DEV-ONLY seed data
--
-- Loaded only when the `dev` profile is active, because application-dev.yaml adds
-- `classpath:db/seed` to spring.flyway.locations. Never referenced by prod.
--
-- FKs are inserted via subselects so the IDENTITY sequences stay untouched
-- (they are never called explicitly). The migration is REPEATABLE-SAFE for a
-- fresh dev database only: it seeds a fixed dataset once, at V1000.
--
-- Dev credentials (BCrypt cost 12, NOT for any real environment):
--   admin@shop.dev     / Admin123!      (ROLE ADMIN)
--   customer@shop.dev  / Customer123!   (ROLE CUSTOMER)
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Categories (two roots, one child) ────────────────────────────────────────
-- Two roots first: a multi-row INSERT cannot see its own rows, so the child's
-- parent subselect only resolves once the roots exist as committed rows.
INSERT INTO categories (name, slug, description, parent_id, active) VALUES
    ('Electronics', 'electronics', 'Gadgets, devices and accessories', NULL, TRUE),
    ('Apparel',     'apparel',     'Clothing and footwear',            NULL, TRUE);

INSERT INTO categories (name, slug, description, parent_id, active) VALUES
    ('Audio', 'audio', 'Headphones, speakers and mics', (SELECT id FROM categories WHERE slug = 'electronics'), TRUE);

-- ── Products ─────────────────────────────────────────────────────────────────
INSERT INTO products
    (sku, name, slug, description, price, compare_at_price, currency, stock_quantity, category_id, image_key, active) VALUES
    ('SKU-HEAD-001', 'Aurora Wireless Headphones',
     'aurora-wireless-headphones',
     'Over-ear Bluetooth 5.3 headphones with active noise cancellation and 40 h battery life.',
     149.99, 189.99, 'USD', 42,
     (SELECT id FROM categories WHERE slug = 'audio'), 'catalog/aurora-headphones/main.jpg', TRUE),
    ('SKU-SPKR-001', 'Pebble Mini Speaker',
     'pebble-mini-speaker',
     'Pocket-sized IPX7 speaker with 12 h playtime and USB-C charging.',
     39.99, NULL, 'USD', 120,
     (SELECT id FROM categories WHERE slug = 'audio'), 'catalog/pebble-speaker/main.jpg', TRUE),
    ('SKU-KEYB-001', 'Tactile 65 Mechanical Keyboard',
     'tactile-65-mechanical-keyboard',
     'Hot-swappable 65% keyboard with brown switches and PBT keycaps.',
     89.50, 99.50, 'USD', 30,
     (SELECT id FROM categories WHERE slug = 'electronics'), 'catalog/tactile-65/main.jpg', TRUE),
    ('SKU-TEE-001', 'Cloudzone Classic Tee',
     'cloudzone-classic-tee',
     'Heavyweight 100% organic cotton tee with a relaxed fit.',
     24.00, NULL, 'USD', 200,
     (SELECT id FROM categories WHERE slug = 'apparel'), 'catalog/classic-tee/main.jpg', TRUE),
    ('SKU-TEE-002', 'Cloudzone Classic Tee — Out of Stock',
     'cloudzone-classic-tee-oos',
     'Same classic tee, used to exercise zero-stock paths in dev.',
     24.00, NULL, 'USD', 0,
     (SELECT id FROM categories WHERE slug = 'apparel'), NULL, TRUE),
    ('SKU-ARCH-001', 'Archived Gadget (inactive)',
     'archived-gadget',
     'Inactive product used to exercise catalogue filtering in dev.',
     59.99, NULL, 'USD', 10,
     (SELECT id FROM categories WHERE slug = 'electronics'), NULL, FALSE);

-- ── Users (dev-only logins) ──────────────────────────────────────────────────
INSERT INTO users (email, password_hash, first_name, last_name, phone, role, auth_provider, enabled, email_verified) VALUES
    ('admin@shop.dev',
     '$2a$12$c1yiz7ZKm6Yy3Ox7bL4d2e/zg0024OWUiceVdNqxClt/kIk9ii/TG',
     'Ada', 'Admin', '+1-555-0100', 'ADMIN', 'LOCAL', TRUE, TRUE),
    ('customer@shop.dev',
     '$2a$12$E//9eI.aZ.q.AiQjBX4xjeZLWx0.4vlgC3THt7CD9ryoQzMLa2IgS',
     'Casey', 'Customer', '+1-555-0101', 'CUSTOMER', 'LOCAL', TRUE, TRUE);

-- ── An address for the customer ──────────────────────────────────────────────
INSERT INTO addresses (user_id, label, recipient_name, line1, line2, city, state, postal_code, country, is_default)
SELECT id, 'HOME', 'Casey Customer', '1 Main Street', 'Apt 4B', 'Springfield', 'IL', '62704', 'US', TRUE
FROM users WHERE email = 'customer@shop.dev';

-- ── An empty cart for the customer (cart gets lines through the API) ─────────
INSERT INTO carts (user_id)
SELECT id FROM users WHERE email = 'customer@shop.dev';
