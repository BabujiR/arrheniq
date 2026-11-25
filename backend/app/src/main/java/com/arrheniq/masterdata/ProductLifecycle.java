package com.arrheniq.masterdata;

import com.arrheniq.kernel.api.ApiException;

import java.util.Map;
import java.util.Set;

/**
 * Product status workflow (URS-MD-002, approval per tenant setting {@code approval.product}; MVP: single
 * approval with e-signature):
 * <pre>
 *   DRAFT ──submit──▶ IN_REVIEW ──approve (sign)──▶ APPROVED
 *     ▲                  │                               │
 *     └──reject (reason)─┘◀──────── edit (reason; signature superseded) ┘
 *   any status except RETIRED ──retire (reason)──▶ RETIRED
 * </pre>
 * Edits are allowed in DRAFT (no reason) and APPROVED (reason required; back to DRAFT).
 */
final class ProductLifecycle {

    enum Action { EDIT, SUBMIT, APPROVE, REJECT, RETIRE }

    private static final Map<Action, Set<String>> ALLOWED_FROM = Map.of(
            Action.EDIT, Set.of("DRAFT", "APPROVED"),
            Action.SUBMIT, Set.of("DRAFT"),
            Action.APPROVE, Set.of("IN_REVIEW"),
            Action.REJECT, Set.of("IN_REVIEW"),
            Action.RETIRE, Set.of("DRAFT", "IN_REVIEW", "APPROVED"));

    private ProductLifecycle() { }

    /** The status after {@code action}, or a 409 if the action is not allowed in {@code status}. */
    static String next(String status, Action action) {
        if (!ALLOWED_FROM.get(action).contains(status)) {
            throw ApiException.invalidState(status, action.name());
        }
        return switch (action) {
            case EDIT, REJECT -> "DRAFT";
            case SUBMIT -> "IN_REVIEW";
            case APPROVE -> "APPROVED";
            case RETIRE -> "RETIRED";
        };
    }
}
