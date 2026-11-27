package com.arrheniq.study;

import com.arrheniq.kernel.api.ApiException;

import java.util.Map;
import java.util.Set;

/**
 * Study workflow (URS-STU-005; approval per tenant setting {@code approval.study}):
 * <pre>
 *   DRAFT ──submit──▶ IN_REVIEW ──approve (sign)──▶ APPROVED (schedule generated in the same transaction)
 *     ▲                  │
 *     └──reject (reason)─┘
 *   DRAFT ──discard (reason)──▶ (logically deleted)
 * </pre>
 * Completion and termination come with S2.4.
 */
final class StudyLifecycle {

    enum Action { EDIT, SUBMIT, APPROVE, REJECT, DISCARD }

    private static final Map<Action, Set<String>> ALLOWED_FROM = Map.of(
            Action.EDIT, Set.of("DRAFT"),
            Action.SUBMIT, Set.of("DRAFT"),
            Action.APPROVE, Set.of("IN_REVIEW"),
            Action.REJECT, Set.of("IN_REVIEW"),
            Action.DISCARD, Set.of("DRAFT"));

    private StudyLifecycle() { }

    static String next(String status, Action action) {
        if (!ALLOWED_FROM.get(action).contains(status)) {
            throw ApiException.invalidState(status, action.name());
        }
        return switch (action) {
            case EDIT, REJECT, DISCARD -> "DRAFT";
            case SUBMIT -> "IN_REVIEW";
            case APPROVE -> "APPROVED";
        };
    }
}
