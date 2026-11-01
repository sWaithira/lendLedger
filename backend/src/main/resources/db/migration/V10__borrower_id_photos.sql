CREATE TABLE borrower_id_image (
                                   id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                   tenant_id    uuid NOT NULL,
                                   borrower_id  uuid NOT NULL,
                                   side         text NOT NULL CHECK (side IN ('FRONT', 'BACK')),
                                   content_type text NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
                                   size_bytes   integer NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 1048576),
                                   data         bytea NOT NULL,
                                   uploaded_by  uuid NOT NULL,
                                   uploaded_at  timestamptz NOT NULL DEFAULT now(),
                                   UNIQUE (tenant_id, borrower_id, side),
                                   FOREIGN KEY (tenant_id, borrower_id) REFERENCES borrower (tenant_id, id),
                                   FOREIGN KEY (tenant_id, uploaded_by) REFERENCES app_user (tenant_id, id)
);

ALTER TABLE borrower
    ADD COLUMN kyc_verified_by uuid,
    ADD COLUMN kyc_verified_at timestamptz,
    ADD COLUMN consent_on date,
    ADD CONSTRAINT borrower_kyc_verified_by_fkey
        FOREIGN KEY (tenant_id, kyc_verified_by) REFERENCES app_user (tenant_id, id);