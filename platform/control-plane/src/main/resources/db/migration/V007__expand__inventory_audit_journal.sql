-- P1-0107 additive facility-scoped inventory audit journal.
-- Events are immutable; the mutable chain head only serializes append operations.

CREATE TABLE platform_core.inventory_audit_chain_head (
    tenant_id uuid NOT NULL,
    facility_id uuid NOT NULL,
    last_sequence bigint NOT NULL DEFAULT 0,
    last_record_hash bytea NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    PRIMARY KEY (tenant_id, facility_id),
    CONSTRAINT fk_inventory_audit_head_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform_core.tenant (tenant_id),
    CONSTRAINT fk_inventory_audit_head_facility
        FOREIGN KEY (facility_id) REFERENCES platform_core.facility (facility_id),
    CONSTRAINT ck_inventory_audit_head_sequence CHECK (last_sequence >= 0),
    CONSTRAINT ck_inventory_audit_head_hash CHECK (octet_length(last_record_hash) = 32)
);

CREATE TABLE platform_core.inventory_audit_event (
    event_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    facility_id uuid NOT NULL,
    scope_sequence bigint NOT NULL,
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL,
    action_code text NOT NULL,
    outcome_code text NOT NULL,
    reason_code text,
    actor_type text NOT NULL,
    actor_id_hash bytea NOT NULL,
    authorized_party text NOT NULL,
    correlation_id uuid NOT NULL,
    trace_id text NOT NULL,
    resource_type text NOT NULL,
    resource_id_hash bytea NOT NULL,
    detail_digest bytea NOT NULL,
    policy_id text NOT NULL,
    policy_version text NOT NULL,
    policy_digest bytea NOT NULL,
    artifact_digest bytea NOT NULL,
    source_id text NOT NULL,
    destination_id text NOT NULL,
    schema_version text NOT NULL,
    integrity_key_id text NOT NULL,
    previous_record_hash bytea NOT NULL,
    record_hash bytea NOT NULL,
    CONSTRAINT uq_inventory_audit_scope_sequence
        UNIQUE (tenant_id, facility_id, scope_sequence),
    CONSTRAINT fk_inventory_audit_event_tenant
        FOREIGN KEY (tenant_id) REFERENCES platform_core.tenant (tenant_id),
    CONSTRAINT fk_inventory_audit_event_facility
        FOREIGN KEY (facility_id) REFERENCES platform_core.facility (facility_id),
    CONSTRAINT ck_inventory_audit_event_id_non_nil
        CHECK (event_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ck_inventory_audit_correlation_non_nil
        CHECK (correlation_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ck_inventory_audit_sequence CHECK (scope_sequence > 0),
    CONSTRAINT ck_inventory_audit_time CHECK (recorded_at >= occurred_at),
    CONSTRAINT ck_inventory_audit_action CHECK (action_code IN (
        'ENDPOINT_READ', 'ENDPOINT_LIST', 'ENDPOINT_CREATE',
        'ENDPOINT_UPDATE', 'ENDPOINT_DECOMMISSION'
    )),
    CONSTRAINT ck_inventory_audit_outcome
        CHECK (outcome_code IN ('SUCCESS', 'FAILURE', 'DENIED')),
    CONSTRAINT ck_inventory_audit_reason
        CHECK ((outcome_code = 'SUCCESS') OR reason_code IS NOT NULL),
    CONSTRAINT ck_inventory_audit_actor_type CHECK (actor_type IN ('HUMAN', 'WORKLOAD')),
    CONSTRAINT ck_inventory_audit_actor_hash CHECK (octet_length(actor_id_hash) = 32),
    CONSTRAINT ck_inventory_audit_authorized_party
        CHECK (char_length(btrim(authorized_party)) BETWEEN 1 AND 256),
    CONSTRAINT ck_inventory_audit_trace_id
        CHECK (trace_id ~ '^[0-9a-f]{32}$' AND trace_id <> repeat('0', 32)),
    CONSTRAINT ck_inventory_audit_resource_type
        CHECK (resource_type IN ('ENDPOINT', 'ENDPOINT_COLLECTION')),
    CONSTRAINT ck_inventory_audit_resource_hash CHECK (octet_length(resource_id_hash) = 32),
    CONSTRAINT ck_inventory_audit_detail_digest CHECK (octet_length(detail_digest) = 32),
    CONSTRAINT ck_inventory_audit_policy_id
        CHECK (char_length(btrim(policy_id)) BETWEEN 1 AND 128),
    CONSTRAINT ck_inventory_audit_policy_version
        CHECK (char_length(btrim(policy_version)) BETWEEN 1 AND 64),
    CONSTRAINT ck_inventory_audit_policy_digest CHECK (octet_length(policy_digest) = 32),
    CONSTRAINT ck_inventory_audit_artifact_digest CHECK (octet_length(artifact_digest) = 32),
    CONSTRAINT ck_inventory_audit_source
        CHECK (char_length(btrim(source_id)) BETWEEN 1 AND 256),
    CONSTRAINT ck_inventory_audit_destination
        CHECK (char_length(btrim(destination_id)) BETWEEN 1 AND 128),
    CONSTRAINT ck_inventory_audit_schema_version CHECK (schema_version = '1.0.0'),
    CONSTRAINT ck_inventory_audit_key_id
        CHECK (integrity_key_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    CONSTRAINT ck_inventory_audit_previous_hash CHECK (octet_length(previous_record_hash) = 32),
    CONSTRAINT ck_inventory_audit_record_hash CHECK (octet_length(record_hash) = 32)
);

CREATE INDEX ix_inventory_audit_scope_time
    ON platform_core.inventory_audit_event (tenant_id, facility_id, recorded_at DESC, event_id);
CREATE INDEX ix_inventory_audit_resource_time
    ON platform_core.inventory_audit_event
        (tenant_id, facility_id, resource_type, resource_id_hash, recorded_at DESC);
CREATE INDEX ix_inventory_audit_actor_time
    ON platform_core.inventory_audit_event
        (tenant_id, facility_id, actor_id_hash, recorded_at DESC);

CREATE FUNCTION platform_core.require_inventory_audit_scope()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM platform_core.facility
         WHERE tenant_id = NEW.tenant_id
           AND facility_id = NEW.facility_id
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23503',
            MESSAGE = 'Inventory audit scope does not identify a facility in the tenant';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_inventory_audit_head_scope
    BEFORE INSERT OR UPDATE OF tenant_id, facility_id
    ON platform_core.inventory_audit_chain_head
    FOR EACH ROW EXECUTE FUNCTION platform_core.require_inventory_audit_scope();
CREATE TRIGGER trg_inventory_audit_event_scope
    BEFORE INSERT ON platform_core.inventory_audit_event
    FOR EACH ROW EXECUTE FUNCTION platform_core.require_inventory_audit_scope();

CREATE FUNCTION platform_core.reject_inventory_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '23000',
        MESSAGE = 'Inventory audit events are append-only';
END;
$$;

CREATE TRIGGER trg_inventory_audit_event_no_update_delete
    BEFORE UPDATE OR DELETE ON platform_core.inventory_audit_event
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_audit_mutation();
CREATE TRIGGER trg_inventory_audit_event_no_truncate
    BEFORE TRUNCATE ON platform_core.inventory_audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION platform_core.reject_inventory_audit_mutation();
