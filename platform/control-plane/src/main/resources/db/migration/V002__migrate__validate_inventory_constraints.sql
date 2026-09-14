-- P1-0102 migrate/validate phase.
-- Run after compatibility code is deployed and validation lock/scan cost is observed.

ALTER TABLE platform_core.tenant
    VALIDATE CONSTRAINT fk_tenant_identity,
    VALIDATE CONSTRAINT fk_tenant_lifecycle;

ALTER TABLE platform_core.organization
    VALIDATE CONSTRAINT fk_organization_identity,
    VALIDATE CONSTRAINT fk_organization_tenant,
    VALIDATE CONSTRAINT fk_organization_lifecycle;

ALTER TABLE platform_core.facility
    VALIDATE CONSTRAINT fk_facility_identity,
    VALIDATE CONSTRAINT fk_facility_organization,
    VALIDATE CONSTRAINT fk_facility_lifecycle;

ALTER TABLE platform_core.application
    VALIDATE CONSTRAINT fk_application_identity,
    VALIDATE CONSTRAINT fk_application_facility,
    VALIDATE CONSTRAINT fk_application_lifecycle;

ALTER TABLE platform_core.endpoint
    VALIDATE CONSTRAINT fk_endpoint_identity,
    VALIDATE CONSTRAINT fk_endpoint_application,
    VALIDATE CONSTRAINT fk_endpoint_lifecycle;

ALTER TABLE platform_core.runtime_cell
    VALIDATE CONSTRAINT fk_runtime_cell_identity,
    VALIDATE CONSTRAINT fk_runtime_cell_lifecycle;

ALTER TABLE platform_core.runtime_cell_scope_assignment
    VALIDATE CONSTRAINT fk_runtime_cell_scope_cell,
    VALIDATE CONSTRAINT fk_runtime_cell_scope_tenant,
    VALIDATE CONSTRAINT fk_runtime_cell_scope_organization,
    VALIDATE CONSTRAINT fk_runtime_cell_scope_facility,
    VALIDATE CONSTRAINT fk_runtime_cell_scope_application,
    VALIDATE CONSTRAINT fk_runtime_cell_scope_endpoint;
