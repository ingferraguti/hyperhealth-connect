package io.hyperhealth.connect.controlplane.security;

import java.util.Arrays;
import java.util.Optional;

/** Closed, minimal role vocabulary for the P1-0104 inventory read slice. */
public enum ControlPlaneRole {
    FACILITY_OPERATOR("FacilityOperator", PrincipalType.HUMAN),
    AUDITOR("Auditor", PrincipalType.HUMAN),
    FLOW_DEVELOPER("FlowDeveloper", PrincipalType.HUMAN),
    RUNTIME_AGENT("RuntimeAgent", PrincipalType.WORKLOAD);

    private final String claimValue;
    private final PrincipalType principalType;

    ControlPlaneRole(String claimValue, PrincipalType principalType) {
        this.claimValue = claimValue;
        this.principalType = principalType;
    }

    public String claimValue() {
        return claimValue;
    }

    public PrincipalType principalType() {
        return principalType;
    }

    static Optional<ControlPlaneRole> fromClaim(String value) {
        return Arrays.stream(values()).filter(role -> role.claimValue.equals(value)).findFirst();
    }
}
