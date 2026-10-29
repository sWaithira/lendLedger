-- V7: the platform operator role, and subscriptions (plan + expiry) per lender.

-- The operator's own role. Allowed only in a tenant flagged is_platform (see the trigger below).
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_role_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check
    CHECK (role IN ('OWNER', 'LOAN_OFFICER', 'PLATFORM_ADMIN'));

ALTER TABLE tenant ADD COLUMN is_platform BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenant ADD COLUMN plan TEXT NOT NULL DEFAULT 'TRIAL' CHECK (plan IN ('TRIAL', 'STANDARD', 'PRO'));
ALTER TABLE tenant ADD COLUMN subscription_expires_on DATE;            -- NULL = no expiry
-- Lenders who register from now on get a 30-day free trial. Existing lenders keep NULL (no expiry).
ALTER TABLE tenant ALTER COLUMN subscription_expires_on SET DEFAULT (current_date + 30);

CREATE FUNCTION guard_platform_admin() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.role = 'PLATFORM_ADMIN'
       AND NOT EXISTS (SELECT 1 FROM tenant t WHERE t.id = NEW.tenant_id AND t.is_platform) THEN
        RAISE EXCEPTION 'PLATFORM_ADMIN is only allowed in the platform tenant';
END IF;
RETURN NEW;
END $$;
CREATE TRIGGER trg_guard_platform_admin
    BEFORE INSERT OR UPDATE OF role, tenant_id ON app_user
    FOR EACH ROW EXECUTE FUNCTION guard_platform_admin();