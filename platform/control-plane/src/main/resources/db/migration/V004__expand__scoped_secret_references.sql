-- P1-0105 additive secret-reference registry.
-- The schema deliberately has no column capable of storing secret material or provider paths.
-- Secret values live only in an external qualified secret store and are resolved at the Runtime Cell.

CREATE TABLE platform_core.secret_reference (
    secret_reference_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    facility_id uuid NOT NULL,
    provider_kind text NOT NULL,
    backend_binding_id uuid NOT NULL,
    purpose text NOT NULL,
    reference_state text NOT NULL DEFAULT 'ACTIVE',
    row_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    revoked_at timestamptz,
    CONSTRAINT uq_secret_reference_scope
        UNIQUE (tenant_id, organization_id, facility_id, secret_reference_id, purpose),
    CONSTRAINT uq_secret_reference_backend_binding
        UNIQUE (tenant_id, organization_id, facility_id, provider_kind, backend_binding_id, purpose),
    CONSTRAINT ck_secret_reference_id_non_nil
        CHECK (secret_reference_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ck_secret_reference_binding_non_nil
        CHECK (backend_binding_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ck_secret_reference_provider CHECK (provider_kind IN (
        'HASHICORP_VAULT',
        'AWS_SECRETS_MANAGER',
        'AZURE_KEY_VAULT',
        'GCP_SECRET_MANAGER',
        'KUBERNETES_SECRETS_STORE_CSI',
        'EXTERNAL_BROKER'
    )),
    CONSTRAINT ck_secret_reference_purpose CHECK (purpose IN (
        'ENDPOINT_BASIC_AUTH',
        'ENDPOINT_API_TOKEN',
        'OAUTH_CLIENT_CREDENTIAL',
        'TLS_CLIENT_PRIVATE_KEY',
        'TLS_CLIENT_CERTIFICATE',
        'DATABASE_CREDENTIAL'
    )),
    CONSTRAINT ck_secret_reference_state
        CHECK (reference_state IN ('ACTIVE', 'ROTATING', 'REVOKED')),
    CONSTRAINT ck_secret_reference_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_secret_reference_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_secret_reference_revocation CHECK (
        (reference_state = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_at >= created_at)
        OR (reference_state <> 'REVOKED' AND revoked_at IS NULL)
    ),
    CONSTRAINT fk_secret_reference_facility
        FOREIGN KEY (tenant_id, organization_id, facility_id)
        REFERENCES platform_core.facility (tenant_id, organization_id, facility_id)
);

CREATE TABLE platform_core.endpoint_secret_binding (
    binding_sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    facility_id uuid NOT NULL,
    application_id uuid NOT NULL,
    endpoint_id uuid NOT NULL,
    secret_reference_id uuid NOT NULL,
    purpose text NOT NULL,
    activated_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    retired_at timestamptz,
    CONSTRAINT ck_endpoint_secret_binding_time
        CHECK (retired_at IS NULL OR retired_at >= activated_at),
    CONSTRAINT fk_endpoint_secret_binding_endpoint
        FOREIGN KEY (tenant_id, organization_id, facility_id, application_id, endpoint_id)
        REFERENCES platform_core.endpoint
            (tenant_id, organization_id, facility_id, application_id, endpoint_id),
    CONSTRAINT fk_endpoint_secret_binding_reference
        FOREIGN KEY (tenant_id, organization_id, facility_id, secret_reference_id, purpose)
        REFERENCES platform_core.secret_reference
            (tenant_id, organization_id, facility_id, secret_reference_id, purpose)
);

-- All indexes are built with the new, empty tables. No populated relation is rewritten or scanned.
CREATE INDEX ix_secret_reference_scope_state
    ON platform_core.secret_reference
        (tenant_id, facility_id, reference_state, secret_reference_id)
    INCLUDE (provider_kind, purpose, row_version);

CREATE UNIQUE INDEX uq_endpoint_secret_binding_active
    ON platform_core.endpoint_secret_binding
        (tenant_id, organization_id, facility_id, application_id, endpoint_id,
         purpose, secret_reference_id)
    WHERE retired_at IS NULL;

CREATE INDEX ix_endpoint_secret_binding_reference_active
    ON platform_core.endpoint_secret_binding
        (tenant_id, facility_id, secret_reference_id, endpoint_id)
    WHERE retired_at IS NULL;

CREATE FUNCTION platform_core.enforce_secret_reference_lifecycle()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.reference_state = OLD.reference_state THEN
        RAISE EXCEPTION 'secret reference lifecycle update must change state'
            USING ERRCODE = '23514';
    END IF;
    IF OLD.reference_state = 'REVOKED' THEN
        RAISE EXCEPTION 'revoked secret reference is terminal'
            USING ERRCODE = '23514';
    END IF;
    IF NEW.row_version <> OLD.row_version + 1 THEN
        RAISE EXCEPTION 'secret reference lifecycle update requires the next row version'
            USING ERRCODE = '23514';
    END IF;
    IF NEW.updated_at < OLD.updated_at THEN
        RAISE EXCEPTION 'secret reference update timestamp cannot move backwards'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION platform_core.enforce_endpoint_secret_binding_retirement()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.retired_at IS NOT NULL OR NEW.retired_at IS NULL THEN
        RAISE EXCEPTION 'secret binding retirement is terminal and append-only'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_secret_reference_immutable
    BEFORE UPDATE OF
        secret_reference_id,
        tenant_id,
        organization_id,
        facility_id,
        provider_kind,
        backend_binding_id,
        purpose,
        created_at
    ON platform_core.secret_reference
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_identity_change();

CREATE TRIGGER trg_secret_reference_no_delete
    BEFORE DELETE ON platform_core.secret_reference
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_hard_delete();

CREATE TRIGGER trg_secret_reference_lifecycle
    BEFORE UPDATE ON platform_core.secret_reference
    FOR EACH ROW EXECUTE FUNCTION platform_core.enforce_secret_reference_lifecycle();

CREATE TRIGGER trg_endpoint_secret_binding_immutable
    BEFORE UPDATE OF
        binding_sequence,
        tenant_id,
        organization_id,
        facility_id,
        application_id,
        endpoint_id,
        secret_reference_id,
        purpose,
        activated_at
    ON platform_core.endpoint_secret_binding
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_identity_change();

CREATE TRIGGER trg_endpoint_secret_binding_no_delete
    BEFORE DELETE ON platform_core.endpoint_secret_binding
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_hard_delete();

CREATE TRIGGER trg_endpoint_secret_binding_retirement
    BEFORE UPDATE ON platform_core.endpoint_secret_binding
    FOR EACH ROW EXECUTE FUNCTION platform_core.enforce_endpoint_secret_binding_retirement();
