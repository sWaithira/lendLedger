-- Engine v2: fixed-fee interest and day/week/month terms. Applies to NEW rule versions only.
ALTER TABLE loan_rule_version DROP CONSTRAINT IF EXISTS loan_rule_version_interest_method_check;
ALTER TABLE loan_rule_version ADD CONSTRAINT loan_rule_version_interest_method_check
    CHECK (interest_method IN ('FLAT', 'REDUCING_BALANCE', 'FIXED_FEE'));

ALTER TABLE loan_rule_version DROP CONSTRAINT IF EXISTS loan_rule_version_period_unit_check;
ALTER TABLE loan_rule_version ADD CONSTRAINT loan_rule_version_period_unit_check
    CHECK (period_unit IN ('DAY', 'WEEK', 'MONTH'));