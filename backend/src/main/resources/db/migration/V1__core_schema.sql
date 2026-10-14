-- LendLedger V1: core schema
-- Conventions: UUID keys, NUMERIC(19,2) money, DATE for business dates, TIMESTAMPTZ for recorded_at.
-- Every business table carries tenant_id. Child tables reference parents through composite
-- (tenant_id, id) foreign keys, so the database itself rejects cross-tenant references (ADR-05).

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- needed for the rule-version overlap constraint

-- ---------------------------------------------------------------- tenant / identity
CREATE TABLE tenant (
                        id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        name        TEXT        NOT NULL,
                        status      TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
                        timezone    TEXT        NOT NULL DEFAULT 'Africa/Nairobi',
                        currency    CHAR(3)     NOT NULL DEFAULT 'KES',
                        created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
                          id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          tenant_id      UUID        NOT NULL REFERENCES tenant (id),
                          name           TEXT        NOT NULL,
                          email          TEXT        NOT NULL,
                          password_hash  TEXT        NOT NULL,
                          role           TEXT        NOT NULL CHECK (role IN ('OWNER', 'LOAN_OFFICER')),
                          status         TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
                          last_login_at  TIMESTAMPTZ,
                          created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                          UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));

-- ---------------------------------------------------------------- borrowers
CREATE TABLE borrower (
                          id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          tenant_id         UUID        NOT NULL REFERENCES tenant (id),
                          full_name         TEXT        NOT NULL,
                          phone             TEXT        NOT NULL,
                          phone_normalized  TEXT        NOT NULL,           -- digits only, e.g. 2547XXXXXXXX
                          note              TEXT,
                          status            TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
                          created_by        UUID        NOT NULL,
                          created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                          UNIQUE (tenant_id, id),
                          FOREIGN KEY (tenant_id, created_by) REFERENCES app_user (tenant_id, id)
);
-- Deliberately NOT unique: a matching phone produces a warning, not a block (F-005).
CREATE INDEX ix_borrower_phone ON borrower (tenant_id, phone_normalized);
CREATE INDEX ix_borrower_name  ON borrower (tenant_id, lower(full_name));

-- ---------------------------------------------------------------- products and rule versions
CREATE TABLE loan_product (
                              id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              tenant_id      UUID          NOT NULL REFERENCES tenant (id),
                              name           TEXT          NOT NULL,
                              currency       CHAR(3)       NOT NULL DEFAULT 'KES',
                              min_principal  NUMERIC(19,2) NOT NULL CHECK (min_principal > 0),
                              max_principal  NUMERIC(19,2) NOT NULL,
                              active         BOOLEAN       NOT NULL DEFAULT TRUE,
                              created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
                              CHECK (max_principal >= min_principal),
                              UNIQUE (tenant_id, id)
);

CREATE TABLE loan_rule_version (
                                   id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   tenant_id                UUID         NOT NULL,
                                   product_id               UUID         NOT NULL,
                                   version_number           INT          NOT NULL CHECK (version_number > 0),
                                   status                   TEXT         NOT NULL DEFAULT 'DRAFT'
                                       CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED')),
                                   effective_from           DATE         NOT NULL,
                                   effective_to             DATE,                    -- exclusive upper bound; NULL = open ended
                                   interest_method          TEXT         NOT NULL CHECK (interest_method IN ('FLAT', 'REDUCING_BALANCE')),
                                   rate_per_period          NUMERIC(9,6) NOT NULL CHECK (rate_per_period >= 0),   -- 0.300000 = 30%
                                   period_unit              TEXT         NOT NULL DEFAULT 'MONTH' CHECK (period_unit = 'MONTH'),
                                   grace_days               INT          NOT NULL DEFAULT 0 CHECK (grace_days >= 0),
                                   penalty_rate_per_period  NUMERIC(9,6) NOT NULL DEFAULT 0 CHECK (penalty_rate_per_period >= 0),
                                   penalty_cap              NUMERIC(19,2) CHECK (penalty_cap IS NULL OR penalty_cap >= 0),
                                   allocation_order         TEXT[]       NOT NULL DEFAULT ARRAY['PENALTY','INTEREST','PRINCIPAL'],
                                   rounding_mode            TEXT         NOT NULL DEFAULT 'HALF_UP' CHECK (rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')),
                                   created_by               UUID,
                                   approved_by              UUID,
                                   created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                   approved_at              TIMESTAMPTZ,
                                   change_reason            TEXT,
                                   UNIQUE (tenant_id, id),
                                   UNIQUE (tenant_id, product_id, version_number),
                                   FOREIGN KEY (tenant_id, product_id)  REFERENCES loan_product (tenant_id, id),
                                   FOREIGN KEY (tenant_id, created_by)  REFERENCES app_user (tenant_id, id),
                                   FOREIGN KEY (tenant_id, approved_by) REFERENCES app_user (tenant_id, id),
                                   CHECK (effective_to IS NULL OR effective_to > effective_from),
                                   CHECK (allocation_order <@ ARRAY['PENALTY','INTEREST','PRINCIPAL']
                                   AND ARRAY['PENALTY','INTEREST','PRINCIPAL'] <@ allocation_order
                                   AND cardinality(allocation_order) = 3),
    CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    -- No two APPROVED versions of one product may overlap in time (3.6.2 / 3.6.5).
    CONSTRAINT ex_rule_version_no_overlap
        EXCLUDE USING gist (product_id WITH =, daterange(effective_from, effective_to, '[)') WITH &&)
        WHERE (status = 'APPROVED')
);

-- Approved versions are immutable (3.6.2). Only status -> RETIRED and effective_to may change.
CREATE FUNCTION guard_rule_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Only DRAFT rule versions can be deleted';
END IF;
RETURN OLD;
END IF;
    IF OLD.status IN ('APPROVED', 'RETIRED') THEN
        IF OLD.status = 'RETIRED' AND NEW.status <> 'RETIRED' THEN
            RAISE EXCEPTION 'A RETIRED rule version cannot be reactivated';
END IF;
        IF NEW.status NOT IN ('APPROVED', 'RETIRED')
           OR (NEW.tenant_id, NEW.product_id, NEW.version_number, NEW.effective_from, NEW.interest_method,
               NEW.rate_per_period, NEW.period_unit, NEW.grace_days, NEW.penalty_rate_per_period,
               NEW.penalty_cap, NEW.allocation_order, NEW.rounding_mode, NEW.approved_by, NEW.approved_at)
              IS DISTINCT FROM
              (OLD.tenant_id, OLD.product_id, OLD.version_number, OLD.effective_from, OLD.interest_method,
               OLD.rate_per_period, OLD.period_unit, OLD.grace_days, OLD.penalty_rate_per_period,
               OLD.penalty_cap, OLD.allocation_order, OLD.rounding_mode, OLD.approved_by, OLD.approved_at)
        THEN
            RAISE EXCEPTION 'Approved rule version % is immutable; create a new version instead', OLD.id;
END IF;
END IF;
RETURN NEW;
END $$;
CREATE TRIGGER trg_guard_rule_version
    BEFORE UPDATE OR DELETE ON loan_rule_version
    FOR EACH ROW EXECUTE FUNCTION guard_rule_version();

-- ---------------------------------------------------------------- loans and ledger
CREATE TABLE loan (
                      id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                      tenant_id        UUID          NOT NULL,
                      borrower_id      UUID          NOT NULL,
                      product_id       UUID          NOT NULL,
                      rule_version_id  UUID          NOT NULL,
                      principal        NUMERIC(19,2) NOT NULL CHECK (principal > 0),
                      currency         CHAR(3)       NOT NULL,
                      issue_date       DATE          NOT NULL,
                      term_periods     INT           NOT NULL CHECK (term_periods > 0),
                      due_date         DATE          NOT NULL,
                      status           TEXT          NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
                      settled_on       DATE,                                -- charges stop accruing from this date
                      terms_snapshot   JSONB         NOT NULL,              -- the agreed terms, copied at origination
                      created_by       UUID          NOT NULL,
                      created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
                      UNIQUE (tenant_id, id),
                      FOREIGN KEY (tenant_id, borrower_id)     REFERENCES borrower (tenant_id, id),
                      FOREIGN KEY (tenant_id, product_id)      REFERENCES loan_product (tenant_id, id),
                      FOREIGN KEY (tenant_id, rule_version_id) REFERENCES loan_rule_version (tenant_id, id),
                      FOREIGN KEY (tenant_id, created_by)      REFERENCES app_user (tenant_id, id),
                      CHECK (due_date > issue_date),
                      CHECK ((status = 'COMPLETED') = (settled_on IS NOT NULL) OR status = 'CANCELLED')
);
CREATE INDEX ix_loan_borrower ON loan (tenant_id, borrower_id);
CREATE INDEX ix_loan_due      ON loan (tenant_id, status, due_date);   -- overdue queue

CREATE TABLE charge_event (
                              id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              tenant_id        UUID          NOT NULL,
                              loan_id          UUID          NOT NULL,
                              charge_type      TEXT          NOT NULL CHECK (charge_type IN ('INTEREST', 'PENALTY')),
                              period_kind      TEXT          NOT NULL CHECK (period_kind IN ('TERM', 'OVERDUE')),
                              period_number    INT           NOT NULL CHECK (period_number > 0),
                              effective_date   DATE          NOT NULL,
                              amount           NUMERIC(19,2) NOT NULL CHECK (amount > 0),
                              rule_version_id  UUID          NOT NULL,
                              created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
                              UNIQUE (tenant_id, id),
    -- Re-running accrual can never double-charge a period.
                              UNIQUE (loan_id, charge_type, period_kind, period_number),
                              FOREIGN KEY (tenant_id, loan_id)         REFERENCES loan (tenant_id, id),
                              FOREIGN KEY (tenant_id, rule_version_id) REFERENCES loan_rule_version (tenant_id, id)
);
CREATE INDEX ix_charge_loan ON charge_event (tenant_id, loan_id, effective_date);

CREATE TABLE repayment (
                           id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           tenant_id        UUID          NOT NULL,
                           loan_id          UUID          NOT NULL,
                           amount           NUMERIC(19,2) NOT NULL CHECK (amount > 0),
                           currency         CHAR(3)       NOT NULL,
                           payment_date     DATE          NOT NULL,
                           recorded_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
                           method           TEXT          NOT NULL CHECK (method IN ('MANUAL', 'CASH', 'MPESA_MANUAL', 'BANK')),
                           reference        TEXT,                                -- unverified metadata, never proof of settlement
                           idempotency_key  TEXT          NOT NULL,
                           request_hash     TEXT          NOT NULL,              -- detects same key + different payload
                           actor_id         UUID          NOT NULL,
                           UNIQUE (tenant_id, id),
                           UNIQUE (tenant_id, idempotency_key),
                           FOREIGN KEY (tenant_id, loan_id)  REFERENCES loan (tenant_id, id),
                           FOREIGN KEY (tenant_id, actor_id) REFERENCES app_user (tenant_id, id)
);
CREATE INDEX ix_repayment_loan ON repayment (tenant_id, loan_id, payment_date, recorded_at);

CREATE TABLE repayment_reversal (
                                    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    tenant_id     UUID        NOT NULL,
                                    repayment_id  UUID        NOT NULL,
                                    loan_id       UUID        NOT NULL,
                                    reason        TEXT        NOT NULL CHECK (length(btrim(reason)) >= 3),
                                    actor_id      UUID        NOT NULL,
                                    reversed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                                    UNIQUE (tenant_id, id),
                                    UNIQUE (repayment_id),                               -- a payment is reversed at most once
                                    FOREIGN KEY (tenant_id, repayment_id) REFERENCES repayment (tenant_id, id),
                                    FOREIGN KEY (tenant_id, loan_id)      REFERENCES loan (tenant_id, id),
                                    FOREIGN KEY (tenant_id, actor_id)     REFERENCES app_user (tenant_id, id)
);

CREATE TABLE payment_allocation (
                                    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    tenant_id     UUID          NOT NULL,
                                    repayment_id  UUID          NOT NULL,
                                    loan_id       UUID          NOT NULL,
                                    reversal_id   UUID,                                   -- NULL = original; set = negative mirror row
                                    component     TEXT          NOT NULL CHECK (component IN ('PENALTY', 'INTEREST', 'PRINCIPAL')),
                                    amount        NUMERIC(19,2) NOT NULL,
                                    sequence_no   INT           NOT NULL CHECK (sequence_no > 0),
                                    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                    CHECK ((reversal_id IS NULL AND amount > 0) OR (reversal_id IS NOT NULL AND amount < 0)),
                                    FOREIGN KEY (tenant_id, repayment_id) REFERENCES repayment (tenant_id, id),
                                    FOREIGN KEY (tenant_id, loan_id)      REFERENCES loan (tenant_id, id),
                                    FOREIGN KEY (tenant_id, reversal_id)  REFERENCES repayment_reversal (tenant_id, id)
);
CREATE INDEX ix_alloc_loan      ON payment_allocation (tenant_id, loan_id);
CREATE INDEX ix_alloc_repayment ON payment_allocation (tenant_id, repayment_id);

-- ---------------------------------------------------------------- audit
CREATE TABLE audit_log (
                           id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                           tenant_id       UUID        NOT NULL REFERENCES tenant (id),
                           actor_id        UUID,
                           action          TEXT        NOT NULL,
                           entity_type     TEXT        NOT NULL,
                           entity_id       TEXT,
                           reason          TEXT,
                           correlation_id  TEXT,
                           detail          JSONB,
                           created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_tenant_time ON audit_log (tenant_id, created_at DESC);
CREATE INDEX ix_audit_entity      ON audit_log (tenant_id, entity_type, entity_id);

-- ---------------------------------------------------------------- insert-only ledger (ADR-07)
CREATE FUNCTION forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is insert-only; % is not allowed', TG_TABLE_NAME, TG_OP;
END $$;

CREATE TRIGGER trg_repayment_insert_only          BEFORE UPDATE OR DELETE ON repayment
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_allocation_insert_only         BEFORE UPDATE OR DELETE ON payment_allocation
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_charge_event_insert_only       BEFORE UPDATE OR DELETE ON charge_event
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_reversal_insert_only           BEFORE UPDATE OR DELETE ON repayment_reversal
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_audit_insert_only              BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();