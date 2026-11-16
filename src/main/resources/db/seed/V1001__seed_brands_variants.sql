-- ─────────────────────────────────────────────────────────────────────────────
-- V1001__seed_brands_variants.sql  —  DEV-ONLY seed data (Step 5: brands + variants)
--
-- Additive companion to V1000: V1000 keeps its shipped content and checksum, so
-- existing dev databases simply apply V1001 on top, and fresh ones run it right
-- after. Never referenced by prod (the `prod` profile loads db/migration only).
--
-- Final dataset (pinned by SeedDataTest): 3 categories, 3 brands, 6 products
-- (5 active, 1 out of stock, 1 inactive), 9 product variants (8 active), 2 users.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Brands ───────────────────────────────────────────────────────────────────
-- The mechanical keyboard and the archived gadget stay unbranded on purpose:
-- `brand_id` is optional, and the catalogue must keep listing products that
-- have no brand.
INSERT INTO brands (name, slug, description, active) VALUES
    ('Aurora Audio',      'aurora',    'Noise-cancelling headphones and audio gear', TRUE),
    ('Pebble Works',      'pebble',    'Pocket-sized portable speakers',            TRUE),
    ('Cloudzone Apparel', 'cloudzone', 'Heavyweight everyday basics',               TRUE);

-- ── Link existing products to their brands ───────────────────────────────────
UPDATE products SET brand_id = (SELECT id FROM brands WHERE slug = 'aurora')
    WHERE slug = 'aurora-wireless-headphones';
UPDATE products SET brand_id = (SELECT id FROM brands WHERE slug = 'pebble')
    WHERE slug = 'pebble-mini-speaker';
UPDATE products SET brand_id = (SELECT id FROM brands WHERE slug = 'cloudzone')
    WHERE slug IN ('cloudzone-classic-tee', 'cloudzone-classic-tee-oos');

-- ── Product variants ─────────────────────────────────────────────────────────
-- Three products carry variants, each covering one rule the API promises:
--   • headphones — null price falls back to the product price; one INACTIVE
--     variant proves the active-only filter;
--   • keyboard   — explicit price overrides, so effectivePrice must prefer them
--     and ordering by price is observable;
--   • tee        — sizes at the product price, including a zero-stock size.
INSERT INTO product_variants
    (product_id, sku, name, options, price, currency, stock_quantity, active) VALUES
    ((SELECT id FROM products WHERE slug = 'aurora-wireless-headphones'),
     'SKU-HW-BLK', 'Midnight Black', 'Colour: Midnight Black', NULL, 'USD', 42, TRUE),
    ((SELECT id FROM products WHERE slug = 'aurora-wireless-headphones'),
     'SKU-HW-WHT', 'Arctic White', 'Colour: Arctic White', NULL, 'USD', 17, TRUE),
    ((SELECT id FROM products WHERE slug = 'aurora-wireless-headphones'),
     'SKU-HW-NVY', 'Matte Navy (retired)', 'Colour: Matte Navy', NULL, 'USD', 5, FALSE),
    ((SELECT id FROM products WHERE slug = 'tactile-65-mechanical-keyboard'),
     'SKU-KEYB-ANSI', 'ANSI Layout', 'Layout: ANSI', 89.50, 'USD', 30, TRUE),
    ((SELECT id FROM products WHERE slug = 'tactile-65-mechanical-keyboard'),
     'SKU-KEYB-ISO', 'ISO Layout', 'Layout: ISO', 94.50, 'USD', 12, TRUE),
    ((SELECT id FROM products WHERE slug = 'cloudzone-classic-tee'),
     'SKU-TEE-S', 'Small', 'Size: S', NULL, 'USD', 60, TRUE),
    ((SELECT id FROM products WHERE slug = 'cloudzone-classic-tee'),
     'SKU-TEE-M', 'Medium', 'Size: M', NULL, 'USD', 85, TRUE),
    ((SELECT id FROM products WHERE slug = 'cloudzone-classic-tee'),
     'SKU-TEE-L', 'Large', 'Size: L', NULL, 'USD', 55, TRUE),
    ((SELECT id FROM products WHERE slug = 'cloudzone-classic-tee'),
     'SKU-TEE-XL', 'Extra Large', 'Size: XL', NULL, 'USD', 0, TRUE);