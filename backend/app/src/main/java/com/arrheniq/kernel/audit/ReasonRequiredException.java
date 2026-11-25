package com.arrheniq.kernel.audit;

import java.io.Serial;

/** A reason for change is required but was not supplied (HTTP 422, problem type {@code reason_required}). */
public class ReasonRequiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String recordType;
    private final String action;

    public ReasonRequiredException(String recordType, String action, String statusBefore) {
        super("ARQ-AUDIT-010: reason required for " + action + " on " + recordType
                + (statusBefore == null ? "" : " in status " + statusBefore));
        this.recordType = recordType;
        this.action = action;
    }

    public String recordType() {
        return recordType;
    }

    public String action() {
        return action;
    }
}
