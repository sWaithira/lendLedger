CREATE TABLE loan_note (
                           id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                           tenant_id  uuid NOT NULL,
                           loan_id    uuid NOT NULL,
                           note       text NOT NULL CHECK (length(btrim(note)) BETWEEN 1 AND 1000),
                           created_by uuid NOT NULL,
                           created_at timestamptz NOT NULL DEFAULT now(),
                           FOREIGN KEY (tenant_id, loan_id) REFERENCES loan (tenant_id, id),
                           FOREIGN KEY (tenant_id, created_by) REFERENCES app_user (tenant_id, id)
);

CREATE INDEX ix_loan_note_loan ON loan_note (tenant_id, loan_id, created_at DESC);