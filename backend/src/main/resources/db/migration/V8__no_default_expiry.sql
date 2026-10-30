-- V8: no automatic expiry. A lender's expiry date is set by the platform operator (for example a free first month).
ALTER TABLE tenant ALTER COLUMN subscription_expires_on DROP DEFAULT;
UPDATE tenant SET subscription_expires_on = NULL WHERE NOT is_platform;