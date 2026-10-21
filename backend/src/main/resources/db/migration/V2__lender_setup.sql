-- V2: per-lender product limits, and one product name per lender.
ALTER TABLE loan_product
    ADD COLUMN max_term_periods INT NOT NULL DEFAULT 12 CHECK (max_term_periods BETWEEN 1 AND 60);

CREATE UNIQUE INDEX ux_loan_product_name ON loan_product (tenant_id, lower(name));