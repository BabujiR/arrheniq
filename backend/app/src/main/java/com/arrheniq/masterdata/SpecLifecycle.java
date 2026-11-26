package com.arrheniq.masterdata;

import com.arrheniq.kernel.api.ApiException;

import java.util.Map;
import java.util.Set;

/**
 * Specification version workflow (URS-MD-004; approval per tenant setting {@code approval.specification}):
 * <pre>
 *   DRAFT ──submit──▶ IN_REVIEW ──approve (sign)──▶ EFFECTIVE ──new version approved──▶ SUPERSEDED
 *     ▲                  │                              │
 *     └──reject (reason)─┘                              └──retire (reason)──▶ RETIRED
 *   DRAFT ──discard (reason)──▶ (logically deleted)
 * </pre>
 * Only a DRAFT is edited. An effective version is never changed: a change is a new version (a copy), approved
 * again; existing results stay linked to the version they were tested against.
 */
final class SpecLifecycle {

    enum Action { EDIT, SUBMIT, APPROVE, REJECT, DISCARD, RETIRE }

    private static final Map<Action, Set<String>> ALLOWED_FROM = Map.of(
            Action.EDIT, Set.of("DRAFT"),
            Action.SUBMIT, Set.of("DRAFT"),
            Action.APPROVE, Set.of("IN_REVIEW"),
            Action.REJECT, Set.of("IN_REVIEW"),
            Action.DISCARD, Set.of("DRAFT"),
            Action.RETIRE, Set.of("EFFECTIVE"));

    private SpecLifecycle() { }

    static String next(String status, Action action) {
        if (!ALLOWED_FROM.get(action).contains(status)) {
            throw ApiException.invalidState(status, action.name());
        }
        return switch (action) {
            case EDIT, REJECT, DISCARD -> "DRAFT";
            case SUBMIT -> "IN_REVIEW";
            case APPROVE -> "EFFECTIVE";
            case RETIRE -> "RETIRED";
        };
    }
}
