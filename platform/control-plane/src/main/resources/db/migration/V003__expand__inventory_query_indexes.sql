-- P1-0102 additive index phase. This migration runs outside a transaction because PostgreSQL
-- forbids CREATE INDEX CONCURRENTLY in a transaction block.

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_resource_identity_type_allocated
    ON platform_core.resource_identity (resource_type, allocated_at, resource_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_tenant_active_updated
    ON platform_core.tenant (updated_at, tenant_id)
    WHERE lifecycle_state <> 'DECOMMISSIONED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_organization_scope_active
    ON platform_core.organization (tenant_id, organization_id)
    INCLUDE (display_name, row_version)
    WHERE lifecycle_state <> 'DECOMMISSIONED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_facility_scope_active
    ON platform_core.facility (tenant_id, organization_id, facility_id)
    INCLUDE (display_name, row_version)
    WHERE lifecycle_state <> 'DECOMMISSIONED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_application_scope_active
    ON platform_core.application (tenant_id, organization_id, facility_id, application_id)
    INCLUDE (display_name, row_version)
    WHERE lifecycle_state <> 'DECOMMISSIONED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_endpoint_scope_active
    ON platform_core.endpoint (tenant_id, organization_id, facility_id, application_id, endpoint_id)
    INCLUDE (display_name, row_version)
    WHERE lifecycle_state <> 'DECOMMISSIONED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_runtime_cell_active_updated
    ON platform_core.runtime_cell (updated_at, runtime_cell_id)
    INCLUDE (display_name, row_version)
    WHERE lifecycle_state <> 'DECOMMISSIONED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_runtime_cell_scope_by_cell_history
    ON platform_core.runtime_cell_scope_assignment (runtime_cell_id, created_at, assignment_sequence);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_runtime_cell_scope_lookup_active
    ON platform_core.runtime_cell_scope_assignment
        (tenant_id, organization_id, facility_id, application_id, endpoint_id, runtime_cell_id)
    WHERE revoked_at IS NULL;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_runtime_cell_scope_assignment_active
    ON platform_core.runtime_cell_scope_assignment
        (runtime_cell_id, scope_level, tenant_id, organization_id, facility_id, application_id, endpoint_id)
    NULLS NOT DISTINCT
    WHERE revoked_at IS NULL;
