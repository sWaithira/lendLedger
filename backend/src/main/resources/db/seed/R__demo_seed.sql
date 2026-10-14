-- Dev/demo seed (repeatable and idempotent). Loaded only when the 'dev' profile adds classpath:db/seed.
-- DEV ONLY: all demo users share the password  Demo#2026pass  (BCrypt hash below). Never deploy this file.
-- Rule values are DEMO DEFAULTS from spec section 13.3, not lender policy.

INSERT INTO tenant (id, name) VALUES
                                  ('11111111-1111-1111-1111-111111111111', 'Demo Lender Ltd'),
                                  ('22222222-2222-2222-2222-222222222222', 'Isolation Test Lender')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO app_user (id, tenant_id, name, email, password_hash, role) VALUES
                                                                           ('cccccccc-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
                                                                            'Demo Owner',   'owner@demo.lendledger.test',   '$2b$10$5OPRJd5FU5lXBe8Dj/TKsOgOyfOfJf5ncjaQx3zQPQQqTdedG48XK', 'OWNER'),
                                                                           ('cccccccc-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
                                                                            'Demo Officer', 'officer@demo.lendledger.test', '$2b$10$5OPRJd5FU5lXBe8Dj/TKsOgOyfOfJf5ncjaQx3zQPQQqTdedG48XK', 'LOAN_OFFICER'),
                                                                           ('cccccccc-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222',
                                                                            'Other Owner',  'owner@other.lendledger.test',  '$2b$10$5OPRJd5FU5lXBe8Dj/TKsOgOyfOfJf5ncjaQx3zQPQQqTdedG48XK', 'OWNER')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO loan_product (id, tenant_id, name, currency, min_principal, max_principal) VALUES
                                                                                           ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
                                                                                            'Monthly Flat Loan', 'KES', 1000.00, 500000.00),
                                                                                           ('aaaaaaaa-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222',
                                                                                            'Monthly Flat Loan', 'KES', 1000.00, 500000.00)
    ON CONFLICT (id) DO NOTHING;

INSERT INTO loan_rule_version
(id, tenant_id, product_id, version_number, status, effective_from, interest_method,
 rate_per_period, grace_days, penalty_rate_per_period, created_by, approved_by, approved_at, change_reason) VALUES
                                                                                                                ('bbbbbbbb-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
                                                                                                                 'aaaaaaaa-0000-0000-0000-000000000001', 1, 'APPROVED', DATE '2026-01-01', 'FLAT',
                                                                                                                 0.300000, 0, 0.000000,
                                                                                                                 'cccccccc-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', now(),
                                                                                                                 'Demo default (spec 13.3)'),
                                                                                                                ('bbbbbbbb-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222',
                                                                                                                 'aaaaaaaa-0000-0000-0000-000000000002', 1, 'APPROVED', DATE '2026-01-01', 'FLAT',
                                                                                                                 0.300000, 0, 0.000000,
                                                                                                                 'cccccccc-0000-0000-0000-000000000003', 'cccccccc-0000-0000-0000-000000000003', now(),
                                                                                                                 'Demo default (spec 13.3)')
    ON CONFLICT (id) DO NOTHING;