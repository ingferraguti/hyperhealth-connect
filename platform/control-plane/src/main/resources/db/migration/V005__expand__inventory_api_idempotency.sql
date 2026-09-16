-- P1-0106 additive idempotency registry for facility-scoped Endpoint creation.
-- Raw Idempotency-Key and raw OIDC subject values are deliberately never persisted.

CREATE TABLE platform_core.inventory_idempotency_record (
    idempotency_record_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id uuid NOT NULL,
    facility_id uuid NOT NULL,
    subject_digest bytea NOT NULL,
    authorized_party text NOT NULL,
    operation_code text NOT NULL,
    idempotency_key_digest bytea NOT NULL,
    request_digest bytea NOT NULL,
    response_endpoint_id uuid,
    response_organization_id uuid,
    response_application_id uuid,
    response_display_name text,
    response_lifecycle_state text,
    response_row_version bigint,
    created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    completed_at timestamptz,
    expires_at timestamptz NOT NULL,
    CONSTRAINT uq_inventory_idempotency_scope_key UNIQUE
        (tenant_id, facility_id, subject_digest, authorized_party,
         operation_code, idempotency_key_digest),
    CONSTRAINT ck_inventory_idempotency_operation
        CHECK (operation_code = 'CREATE_ENDPOINT'),
    CONSTRAINT ck_inventory_idempotency_digests CHECK (
        octet_length(subject_digest) = 32
        AND octet_length(idempotency_key_digest) = 32
        AND octet_length(request_digest) = 32
    ),
    CONSTRAINT ck_inventory_idempotency_client
        CHECK (char_length(btrim(authorized_party)) BETWEEN 1 AND 256),
    CONSTRAINT ck_inventory_idempotency_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT ck_inventory_idempotency_completion CHECK (
        (completed_at IS NULL
            AND response_endpoint_id IS NULL
            AND response_organization_id IS NULL
            AND response_application_id IS NULL
            AND response_display_name IS NULL
            AND response_lifecycle_state IS NULL
            AND response_row_version IS NULL)
        OR
        (completed_at IS NOT NULL
            AND completed_at >= created_at
            AND response_endpoint_id IS NOT NULL
            AND response_organization_id IS NOT NULL
            AND response_application_id IS NOT NULL
            AND response_display_name IS NOT NULL
            AND response_lifecycle_state IS NOT NULL
            AND response_row_version IS NOT NULL)
    ),
    CONSTRAINT ck_inventory_idempotency_response_state
        CHECK (response_lifecycle_state IS NULL OR response_lifecycle_state IN
            ('ACTIVE', 'SUSPENDED', 'DECOMMISSIONED')),
    CONSTRAINT ck_inventory_idempotency_response_version
        CHECK (response_row_version IS NULL OR response_row_version >= 0),
    CONSTRAINT fk_inventory_idempotency_response_endpoint
        FOREIGN KEY
            (tenant_id, response_organization_id, facility_id,
             response_application_id, response_endpoint_id)
        REFERENCES platform_core.endpoint
            (tenant_id, organization_id, facility_id, application_id, endpoint_id)
);

CREATE INDEX ix_inventory_idempotency_expiry
    ON platform_core.inventory_idempotency_record (expires_at, idempotency_record_id);

COMMENT ON TABLE platform_core.inventory_idempotency_record IS
    'Ephemeral, scoped replay records. Contains digests and an allowlisted response snapshot; never raw keys, raw subjects, secrets, locators, or clinical payloads.';
