-- P1-0102 expand phase: additive inventory schema for PostgreSQL 18.x.
-- Forward-only. Do not modify after release; add a new versioned migration instead.

CREATE TABLE platform_core.lifecycle_state (
    state_code text PRIMARY KEY,
    terminal boolean NOT NULL,
    CONSTRAINT ck_lifecycle_state_code
        CHECK (state_code IN ('ACTIVE', 'SUSPENDED', 'DECOMMISSIONED'))
);

INSERT INTO platform_core.lifecycle_state (state_code, terminal)
VALUES
    ('ACTIVE', false),
    ('SUSPENDED', false),
    ('DECOMMISSIONED', true);

-- Append-only identity allocation ledger. Keeping this row after decommission prevents reuse
-- across resource kinds, restarts, restores, and later contract migrations.
CREATE TABLE platform_core.resource_identity (
    resource_id uuid PRIMARY KEY,
    resource_type text NOT NULL,
    allocated_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    decommissioned_at timestamptz,
    CONSTRAINT uq_resource_identity_id_type UNIQUE (resource_id, resource_type),
    CONSTRAINT ck_resource_identity_non_nil
        CHECK (resource_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ck_resource_identity_type
        CHECK (resource_type IN ('TENANT', 'ORGANIZATION', 'FACILITY', 'APPLICATION', 'ENDPOINT', 'RUNTIME_CELL')),
    CONSTRAINT ck_resource_identity_decommission_time
        CHECK (decommissioned_at IS NULL OR decommissioned_at >= allocated_at)
);

CREATE TABLE platform_core.tenant (
    tenant_id uuid PRIMARY KEY,
    resource_type text NOT NULL DEFAULT 'TENANT',
    display_name text NOT NULL,
    lifecycle_state text NOT NULL DEFAULT 'ACTIVE',
    row_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    decommissioned_at timestamptz,
    CONSTRAINT uq_tenant_identity_type UNIQUE (tenant_id, resource_type),
    CONSTRAINT ck_tenant_resource_type CHECK (resource_type = 'TENANT'),
    CONSTRAINT ck_tenant_display_name CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 256),
    CONSTRAINT ck_tenant_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_tenant_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_tenant_lifecycle_time CHECK ((lifecycle_state = 'DECOMMISSIONED') = (decommissioned_at IS NOT NULL))
);

CREATE TABLE platform_core.organization (
    organization_id uuid PRIMARY KEY,
    resource_type text NOT NULL DEFAULT 'ORGANIZATION',
    tenant_id uuid NOT NULL,
    display_name text NOT NULL,
    lifecycle_state text NOT NULL DEFAULT 'ACTIVE',
    row_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    decommissioned_at timestamptz,
    CONSTRAINT uq_organization_identity_type UNIQUE (organization_id, resource_type),
    CONSTRAINT uq_organization_scope UNIQUE (tenant_id, organization_id),
    CONSTRAINT ck_organization_resource_type CHECK (resource_type = 'ORGANIZATION'),
    CONSTRAINT ck_organization_display_name CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 256),
    CONSTRAINT ck_organization_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_organization_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_organization_lifecycle_time CHECK ((lifecycle_state = 'DECOMMISSIONED') = (decommissioned_at IS NOT NULL))
);

CREATE TABLE platform_core.facility (
    facility_id uuid PRIMARY KEY,
    resource_type text NOT NULL DEFAULT 'FACILITY',
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    display_name text NOT NULL,
    lifecycle_state text NOT NULL DEFAULT 'ACTIVE',
    row_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    decommissioned_at timestamptz,
    CONSTRAINT uq_facility_identity_type UNIQUE (facility_id, resource_type),
    CONSTRAINT uq_facility_scope UNIQUE (tenant_id, organization_id, facility_id),
    CONSTRAINT ck_facility_resource_type CHECK (resource_type = 'FACILITY'),
    CONSTRAINT ck_facility_display_name CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 256),
    CONSTRAINT ck_facility_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_facility_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_facility_lifecycle_time CHECK ((lifecycle_state = 'DECOMMISSIONED') = (decommissioned_at IS NOT NULL))
);

CREATE TABLE platform_core.application (
    application_id uuid PRIMARY KEY,
    resource_type text NOT NULL DEFAULT 'APPLICATION',
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    facility_id uuid NOT NULL,
    display_name text NOT NULL,
    lifecycle_state text NOT NULL DEFAULT 'ACTIVE',
    row_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    decommissioned_at timestamptz,
    CONSTRAINT uq_application_identity_type UNIQUE (application_id, resource_type),
    CONSTRAINT uq_application_scope UNIQUE (tenant_id, organization_id, facility_id, application_id),
    CONSTRAINT ck_application_resource_type CHECK (resource_type = 'APPLICATION'),
    CONSTRAINT ck_application_display_name CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 256),
    CONSTRAINT ck_application_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_application_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_application_lifecycle_time CHECK ((lifecycle_state = 'DECOMMISSIONED') = (decommissioned_at IS NOT NULL))
);

CREATE TABLE platform_core.endpoint (
    endpoint_id uuid PRIMARY KEY,
    resource_type text NOT NULL DEFAULT 'ENDPOINT',
    tenant_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    facility_id uuid NOT NULL,
    application_id uuid NOT NULL,
    display_name text NOT NULL,
    lifecycle_state text NOT NULL DEFAULT 'ACTIVE',
    row_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    decommissioned_at timestamptz,
    CONSTRAINT uq_endpoint_identity_type UNIQUE (endpoint_id, resource_type),
    CONSTRAINT uq_endpoint_scope UNIQUE (tenant_id, organization_id, facility_id, application_id, endpoint_id),
    CONSTRAINT ck_endpoint_resource_type CHECK (resource_type = 'ENDPOINT'),
    CONSTRAINT ck_endpoint_display_name CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 256),
    CONSTRAINT ck_endpoint_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_endpoint_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_endpoint_lifecycle_time CHECK ((lifecycle_state = 'DECOMMISSIONED') = (decommissioned_at IS NOT NULL))
);

CREATE TABLE platform_core.runtime_cell (
    runtime_cell_id uuid PRIMARY KEY,
    resource_type text NOT NULL DEFAULT 'RUNTIME_CELL',
    display_name text NOT NULL,
    lifecycle_state text NOT NULL DEFAULT 'ACTIVE',
    row_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    decommissioned_at timestamptz,
    CONSTRAINT uq_runtime_cell_identity_type UNIQUE (runtime_cell_id, resource_type),
    CONSTRAINT ck_runtime_cell_resource_type CHECK (resource_type = 'RUNTIME_CELL'),
    CONSTRAINT ck_runtime_cell_display_name CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 256),
    CONSTRAINT ck_runtime_cell_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_runtime_cell_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_runtime_cell_lifecycle_time CHECK ((lifecycle_state = 'DECOMMISSIONED') = (decommissioned_at IS NOT NULL))
);

CREATE TABLE platform_core.runtime_cell_scope_assignment (
    assignment_sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    runtime_cell_id uuid NOT NULL,
    scope_level text NOT NULL,
    tenant_id uuid NOT NULL,
    organization_id uuid,
    facility_id uuid,
    application_id uuid,
    endpoint_id uuid,
    created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    revoked_at timestamptz,
    CONSTRAINT ck_runtime_cell_scope_level
        CHECK (scope_level IN ('TENANT', 'ORGANIZATION', 'FACILITY', 'APPLICATION', 'ENDPOINT')),
    CONSTRAINT ck_runtime_cell_scope_shape CHECK (
        (scope_level = 'TENANT' AND organization_id IS NULL AND facility_id IS NULL AND application_id IS NULL AND endpoint_id IS NULL)
        OR (scope_level = 'ORGANIZATION' AND organization_id IS NOT NULL AND facility_id IS NULL AND application_id IS NULL AND endpoint_id IS NULL)
        OR (scope_level = 'FACILITY' AND organization_id IS NOT NULL AND facility_id IS NOT NULL AND application_id IS NULL AND endpoint_id IS NULL)
        OR (scope_level = 'APPLICATION' AND organization_id IS NOT NULL AND facility_id IS NOT NULL AND application_id IS NOT NULL AND endpoint_id IS NULL)
        OR (scope_level = 'ENDPOINT' AND organization_id IS NOT NULL AND facility_id IS NOT NULL AND application_id IS NOT NULL AND endpoint_id IS NOT NULL)
    ),
    CONSTRAINT ck_runtime_cell_scope_revoked_time CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

-- FKs are added NOT VALID during expand so an upgrade does not scan populated tables while
-- holding the DDL lock. PostgreSQL still checks every row written after the constraint is added.
-- V002 performs the controlled validation phase.
ALTER TABLE platform_core.tenant
    ADD CONSTRAINT fk_tenant_identity FOREIGN KEY (tenant_id, resource_type)
        REFERENCES platform_core.resource_identity (resource_id, resource_type) NOT VALID,
    ADD CONSTRAINT fk_tenant_lifecycle FOREIGN KEY (lifecycle_state)
        REFERENCES platform_core.lifecycle_state (state_code) NOT VALID;

ALTER TABLE platform_core.organization
    ADD CONSTRAINT fk_organization_identity FOREIGN KEY (organization_id, resource_type)
        REFERENCES platform_core.resource_identity (resource_id, resource_type) NOT VALID,
    ADD CONSTRAINT fk_organization_tenant FOREIGN KEY (tenant_id)
        REFERENCES platform_core.tenant (tenant_id) NOT VALID,
    ADD CONSTRAINT fk_organization_lifecycle FOREIGN KEY (lifecycle_state)
        REFERENCES platform_core.lifecycle_state (state_code) NOT VALID;

ALTER TABLE platform_core.facility
    ADD CONSTRAINT fk_facility_identity FOREIGN KEY (facility_id, resource_type)
        REFERENCES platform_core.resource_identity (resource_id, resource_type) NOT VALID,
    ADD CONSTRAINT fk_facility_organization FOREIGN KEY (tenant_id, organization_id)
        REFERENCES platform_core.organization (tenant_id, organization_id) NOT VALID,
    ADD CONSTRAINT fk_facility_lifecycle FOREIGN KEY (lifecycle_state)
        REFERENCES platform_core.lifecycle_state (state_code) NOT VALID;

ALTER TABLE platform_core.application
    ADD CONSTRAINT fk_application_identity FOREIGN KEY (application_id, resource_type)
        REFERENCES platform_core.resource_identity (resource_id, resource_type) NOT VALID,
    ADD CONSTRAINT fk_application_facility FOREIGN KEY (tenant_id, organization_id, facility_id)
        REFERENCES platform_core.facility (tenant_id, organization_id, facility_id) NOT VALID,
    ADD CONSTRAINT fk_application_lifecycle FOREIGN KEY (lifecycle_state)
        REFERENCES platform_core.lifecycle_state (state_code) NOT VALID;

ALTER TABLE platform_core.endpoint
    ADD CONSTRAINT fk_endpoint_identity FOREIGN KEY (endpoint_id, resource_type)
        REFERENCES platform_core.resource_identity (resource_id, resource_type) NOT VALID,
    ADD CONSTRAINT fk_endpoint_application FOREIGN KEY (tenant_id, organization_id, facility_id, application_id)
        REFERENCES platform_core.application (tenant_id, organization_id, facility_id, application_id) NOT VALID,
    ADD CONSTRAINT fk_endpoint_lifecycle FOREIGN KEY (lifecycle_state)
        REFERENCES platform_core.lifecycle_state (state_code) NOT VALID;

ALTER TABLE platform_core.runtime_cell
    ADD CONSTRAINT fk_runtime_cell_identity FOREIGN KEY (runtime_cell_id, resource_type)
        REFERENCES platform_core.resource_identity (resource_id, resource_type) NOT VALID,
    ADD CONSTRAINT fk_runtime_cell_lifecycle FOREIGN KEY (lifecycle_state)
        REFERENCES platform_core.lifecycle_state (state_code) NOT VALID;

ALTER TABLE platform_core.runtime_cell_scope_assignment
    ADD CONSTRAINT fk_runtime_cell_scope_cell FOREIGN KEY (runtime_cell_id)
        REFERENCES platform_core.runtime_cell (runtime_cell_id) NOT VALID,
    ADD CONSTRAINT fk_runtime_cell_scope_tenant FOREIGN KEY (tenant_id)
        REFERENCES platform_core.tenant (tenant_id) NOT VALID,
    ADD CONSTRAINT fk_runtime_cell_scope_organization FOREIGN KEY (tenant_id, organization_id)
        REFERENCES platform_core.organization (tenant_id, organization_id) NOT VALID,
    ADD CONSTRAINT fk_runtime_cell_scope_facility FOREIGN KEY (tenant_id, organization_id, facility_id)
        REFERENCES platform_core.facility (tenant_id, organization_id, facility_id) NOT VALID,
    ADD CONSTRAINT fk_runtime_cell_scope_application FOREIGN KEY (tenant_id, organization_id, facility_id, application_id)
        REFERENCES platform_core.application (tenant_id, organization_id, facility_id, application_id) NOT VALID,
    ADD CONSTRAINT fk_runtime_cell_scope_endpoint FOREIGN KEY (tenant_id, organization_id, facility_id, application_id, endpoint_id)
        REFERENCES platform_core.endpoint (tenant_id, organization_id, facility_id, application_id, endpoint_id) NOT VALID;

CREATE FUNCTION platform_core.reject_inventory_identity_change()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '23000',
        MESSAGE = format('%s identity or hierarchy columns are immutable', TG_TABLE_NAME);
END;
$$;

CREATE FUNCTION platform_core.reject_inventory_hard_delete()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '23000',
        MESSAGE = format('%s is append/decommission only; hard delete is forbidden', TG_TABLE_NAME);
END;
$$;

CREATE TRIGGER trg_resource_identity_immutable
    BEFORE UPDATE OF resource_id, resource_type ON platform_core.resource_identity
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_identity_change();
CREATE TRIGGER trg_resource_identity_no_delete
    BEFORE DELETE ON platform_core.resource_identity
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_hard_delete();

CREATE TRIGGER trg_tenant_immutable
    BEFORE UPDATE OF tenant_id, resource_type ON platform_core.tenant
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_identity_change();
CREATE TRIGGER trg_tenant_no_delete
    BEFORE DELETE ON platform_core.tenant
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_hard_delete();

CREATE TRIGGER trg_organization_immutable
    BEFORE UPDATE OF organization_id, resource_type, tenant_id ON platform_core.organization
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_identity_change();
CREATE TRIGGER trg_organization_no_delete
    BEFORE DELETE ON platform_core.organization
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_hard_delete();

CREATE TRIGGER trg_facility_immutable
    BEFORE UPDATE OF facility_id, resource_type, tenant_id, organization_id ON platform_core.facility
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_identity_change();
CREATE TRIGGER trg_facility_no_delete
    BEFORE DELETE ON platform_core.facility
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_hard_delete();

CREATE TRIGGER trg_application_immutable
    BEFORE UPDATE OF application_id, resource_type, tenant_id, organization_id, facility_id ON platform_core.application
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_identity_change();
CREATE TRIGGER trg_application_no_delete
    BEFORE DELETE ON platform_core.application
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_hard_delete();

CREATE TRIGGER trg_endpoint_immutable
    BEFORE UPDATE OF endpoint_id, resource_type, tenant_id, organization_id, facility_id, application_id ON platform_core.endpoint
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_identity_change();
CREATE TRIGGER trg_endpoint_no_delete
    BEFORE DELETE ON platform_core.endpoint
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_hard_delete();

CREATE TRIGGER trg_runtime_cell_immutable
    BEFORE UPDATE OF runtime_cell_id, resource_type ON platform_core.runtime_cell
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_identity_change();
CREATE TRIGGER trg_runtime_cell_no_delete
    BEFORE DELETE ON platform_core.runtime_cell
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_hard_delete();

CREATE TRIGGER trg_runtime_cell_scope_immutable
    BEFORE UPDATE OF assignment_sequence, runtime_cell_id, scope_level, tenant_id, organization_id, facility_id, application_id, endpoint_id
    ON platform_core.runtime_cell_scope_assignment
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_identity_change();
CREATE TRIGGER trg_runtime_cell_scope_no_delete
    BEFORE DELETE ON platform_core.runtime_cell_scope_assignment
    FOR EACH ROW EXECUTE FUNCTION platform_core.reject_inventory_hard_delete();

CREATE FUNCTION platform_core.require_active_runtime_cell_scope()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
DECLARE
    checked_cell_id uuid;
BEGIN
    checked_cell_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.runtime_cell_id ELSE NEW.runtime_cell_id END;

    IF EXISTS (
        SELECT 1
        FROM platform_core.runtime_cell
        WHERE runtime_cell_id = checked_cell_id
          AND lifecycle_state <> 'DECOMMISSIONED'
    ) AND NOT EXISTS (
        SELECT 1
        FROM platform_core.runtime_cell_scope_assignment
        WHERE runtime_cell_id = checked_cell_id
          AND revoked_at IS NULL
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'An active runtime cell requires at least one active scope assignment';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_runtime_cell_requires_scope
    AFTER INSERT OR UPDATE OF lifecycle_state, decommissioned_at ON platform_core.runtime_cell
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION platform_core.require_active_runtime_cell_scope();

CREATE CONSTRAINT TRIGGER trg_runtime_cell_scope_preserves_assignment
    AFTER INSERT OR UPDATE OF revoked_at ON platform_core.runtime_cell_scope_assignment
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION platform_core.require_active_runtime_cell_scope();
