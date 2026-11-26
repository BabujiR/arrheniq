package com.arrheniq.masterdata;

import com.arrheniq.kernel.api.ApiException;

import java.util.Map;
import java.util.Set;

/**
 * Protocol version workflow (URS-PRO-005; approval per tenant setting {@code approval.protocol}):
 * <pre>
 *   DRAFT ──submit──▶ IN_REVIEW ──approve (sign)──▶ APPROVED ──new version approved──▶ SUPERSEDED
 *     ▲                  │                              │
 *     └──reject (reason)─┘                              └──retire (reason)──▶ RETIRED
 *   DRAFT ──discard (reason)──▶ (logically deleted)
 * </pre>
 * Only an APPROVED version can be used to log in a study (S2). It never changes: a change is a new version.
 */
final class ProtocolLifecycle {

    enum Action { EDIT, SUBMIT, APPROVE, REJECT, DISCARD, RETIRE }

    private static final Map<Action, Set<String>> ALLOWED_FROM = Map.of(
            Action.EDIT, Set.of("DRAFT"),
            Action.SUBMIT, Set.of("DRAFT"),
            Action.APPROVE, Set.of("IN_REVIEW"),
            Action.REJECT, Set.of("IN_REVIEW"),
            Action.DISCARD, Set.of("DRAFT"),
            Action.RETIRE, Set.of("APPROVED"));

    private ProtocolLifecycle() { }

    static String next(String status, Action action) {
        if (!ALLOWED_FROM.get(action).contains(status)) {
            throw ApiException.invalidState(status, action.name());
        }
        return switch (action) {
            case EDIT, REJECT, DISCARD -> "DRAFT";
            case SUBMIT -> "IN_REVIEW";
            case APPROVE -> "APPROVED";
            case RETIRE -> "RETIRED";
        };
    }
}
