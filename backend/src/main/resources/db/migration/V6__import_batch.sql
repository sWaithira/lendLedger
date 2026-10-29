-- V6: remembers which Excel files a lender has imported, so the same file cannot be imported twice.
CREATE TABLE import_batch (
                              id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              tenant_id         UUID        NOT NULL REFERENCES tenant (id),
                              file_hash         TEXT        NOT NULL,
                              file_name         TEXT        NOT NULL,
                              borrowers_created INT         NOT NULL,
                              loans_created     INT         NOT NULL,
                              created_by        UUID        NOT NULL,
                              created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                              UNIQUE (tenant_id, file_hash),
                              FOREIGN KEY (tenant_id, created_by) REFERENCES app_user (tenant_id, id)
);