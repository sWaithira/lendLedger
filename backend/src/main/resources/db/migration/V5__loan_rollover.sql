-- V5: rollover. A rolled loan is closed by a non-cash ROLLOVER row that carries its principal into a new loan.
ALTER TABLE repayment DROP CONSTRAINT IF EXISTS repayment_method_check;
ALTER TABLE repayment ADD CONSTRAINT repayment_method_check
    CHECK (method IN ('MANUAL', 'CASH', 'MPESA_MANUAL', 'BANK', 'ROLLOVER'));

ALTER TABLE loan ADD COLUMN rolled_from_loan_id UUID;
ALTER TABLE loan ADD CONSTRAINT fk_loan_rolled_from
    FOREIGN KEY (tenant_id, rolled_from_loan_id) REFERENCES loan (tenant_id, id);