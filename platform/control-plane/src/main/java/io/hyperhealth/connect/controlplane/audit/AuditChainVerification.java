package io.hyperhealth.connect.controlplane.audit;

/** Result of deterministic verification against events and the current scope head. */
public record AuditChainVerification(boolean valid, long eventCount, Long firstInvalidSequence) {
    static AuditChainVerification valid(long eventCount) {
        return new AuditChainVerification(true, eventCount, null);
    }

    static AuditChainVerification invalid(long eventCount, long sequence) {
        return new AuditChainVerification(false, eventCount, sequence);
    }
}
