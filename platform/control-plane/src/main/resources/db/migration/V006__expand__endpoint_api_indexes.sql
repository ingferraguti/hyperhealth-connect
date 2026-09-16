-- P1-0106 keyset-pagination indexes. Run outside a transaction to avoid blocking writes
-- when upgrading an existing enterprise inventory.

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_endpoint_facility_page_active
    ON platform_core.endpoint (tenant_id, facility_id, endpoint_id)
    INCLUDE (organization_id, application_id, display_name, lifecycle_state, row_version)
    WHERE lifecycle_state <> 'DECOMMISSIONED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_endpoint_facility_application_page_active
    ON platform_core.endpoint (tenant_id, facility_id, application_id, endpoint_id)
    INCLUDE (organization_id, display_name, lifecycle_state, row_version)
    WHERE lifecycle_state <> 'DECOMMISSIONED';
