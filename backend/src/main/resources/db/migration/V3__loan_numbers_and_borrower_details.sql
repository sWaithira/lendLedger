ALTER TABLE loan ADD COLUMN loan_number INT;
UPDATE loan l SET loan_number = n.rn
    FROM (SELECT id, row_number() OVER (PARTITION BY tenant_id ORDER BY created_at, id) AS rn FROM loan) n
WHERE n.id = l.id;
ALTER TABLE loan ALTER COLUMN loan_number SET NOT NULL;
ALTER TABLE loan ADD CONSTRAINT uq_loan_number UNIQUE (tenant_id, loan_number);

CREATE TABLE tenant_counter (
                                tenant_id  UUID NOT NULL REFERENCES tenant (id),
                                name       TEXT NOT NULL,
                                next_value INT  NOT NULL CHECK (next_value > 0),
                                PRIMARY KEY (tenant_id, name)
);
INSERT INTO tenant_counter (tenant_id, name, next_value)
SELECT tenant_id, 'loan', max(loan_number) + 1 FROM loan GROUP BY tenant_id;

ALTER TABLE borrower
    ADD COLUMN email TEXT,
    ADD COLUMN national_id TEXT,
    ADD COLUMN kyc_status TEXT NOT NULL DEFAULT 'NOT_VERIFIED' CHECK (kyc_status IN ('NOT_VERIFIED', 'VERIFIED'));