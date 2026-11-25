package com.arrheniq.kernel.audit;

import java.util.Locale;
import java.util.Set;

/**
 * Server-side rules for when a reason for change is mandatory (audit-esignature.md §5;
 * URS-AUD-004; fixes legacy BR-061 where reasons were optional and client-supplied).
 * <p>
 * Always required (not configurable):
 * <ul>
 *   <li>any change to a record whose current status is locked (approved, effective, completed, signed…)</li>
 *   <li>logical deletion, reversals, manual schedule date changes, extra pulls, re-opening</li>
 * </ul>
 * Tenants may add actions via {@code additionalActions} (tenant setting, versioned + audited).
 */
public final class ReasonPolicy {

    /** Record statuses after which every change needs a reason. */
    public static final Set<String> LOCKED_STATUSES = Set.of(
            "APPROVED", "EFFECTIVE", "COMPLETED", "SIGNED", "L1_APPROVED", "SUBMITTED", "TERMINATED", "SUPERSEDED");

    /** Actions that always need a reason, whatever the status. */
    public static final Set<String> ALWAYS_REQUIRED_ACTIONS = Set.of(
            "LOGICAL_DELETE", "REVERSAL", "SCHEDULE_DATE_CHANGE", "EXTRA_PULL", "REOPEN", "SIGNATURE_REVOKE");

    /** Actions that never need a reason (creation, signing itself, logins). */
    private static final Set<String> EXEMPT_ACTIONS = Set.of("CREATE", "SIGN", "LOGIN_SUCCESS", "LOGIN_FAILURE", "EXPORT");

    private final Set<String> additionalActions;

    public ReasonPolicy(Set<String> additionalActions) {
        this.additionalActions = Set.copyOf(additionalActions);
    }

    public static ReasonPolicy defaults() {
        return new ReasonPolicy(Set.of());
    }

    public boolean requiresReason(AuditEventDraft draft) {
        String action = draft.action().toUpperCase(Locale.ROOT);
        if (ALWAYS_REQUIRED_ACTIONS.contains(action) || additionalActions.contains(action)) {
            return true;
        }
        if (EXEMPT_ACTIONS.contains(action)) {
            return false;
        }
        return draft.statusBefore() != null
                && LOCKED_STATUSES.contains(draft.statusBefore().toUpperCase(Locale.ROOT));
    }

    /** Throws {@link ReasonRequiredException} (mapped to HTTP 422) if a required reason is missing. */
    public void check(AuditEventDraft draft) {
        if (draft.reason() == null && requiresReason(draft)) {
            throw new ReasonRequiredException(draft.recordType(), draft.action(), draft.statusBefore());
        }
    }
}
