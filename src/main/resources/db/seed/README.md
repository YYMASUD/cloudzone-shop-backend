# Seed data for the dev profile only.
# Files here are loaded by Flyway when spring.profiles.active=dev
# (see application-dev.yaml → spring.flyway.locations).
#
# Naming convention: same as migrations but start with an R (repeatable):
#   R__seed_categories.sql
#   R__seed_products.sql
#
# Or use versioned seeds if order matters:
#   V1000__seed_dev_data.sql
