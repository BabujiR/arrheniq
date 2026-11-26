package com.arrheniq.kernel.esign;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.security.CurrentUserHolder;
import com.arrheniq.kernel.security.ReauthEvidence;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The approval step shared by every approvable record (specifications, protocols, products, …): checks
 * segregation of duties, decides the meaning of the next signature from the configured {@link ApprovalLevel},
 * and signs. The calling module checks the permission and the record's status, opens the audit context, and
 * makes the record effective when {@link Step#complete()}.
 * <p>
 * Rules (URS-ESG-001..003, URS-MD-004; Part 11 §11.50, §11.70):
 * <ul>
 *   <li>the author of the content and the submitter can't sign it ({@code ARQ-SOD-001})</li>
 *   <li>with DOUBLE, the second signer must be a different person ({@code ARQ-SOD-002})</li>
 *   <li>only signatures that are still VALID count: an edit or a rejection supersedes them</li>
 *   <li>the re-authentication evidence is consumed only after these checks, so a refused attempt doesn't waste it</li>
 * </ul>
 */
@Service
public class ApprovalService {

    public static final String MEANING_REVIEWED = "REVIEWED";
    public static final String MEANING_APPROVED = "APPROVED";

    private final ApprovalPolicy policy;
    private final SignatureService signatures;

    public ApprovalService(ApprovalPolicy policy, SignatureService signatures) {
        this.policy = policy;
        this.signatures = signatures;
    }

    /**
     * A record presented for signature.
     *
     * @param kind               setting suffix, e.g. {@code product} for {@code approval.product}
     * @param excludedApprovers  who may not sign: last content editor and submitter
     */
    public record Approvable(String recordType, String kind, UUID id, long version, @Nullable String businessKey,
                             @Nullable UUID siteId, Map<String, ? extends @Nullable Object> content,
                             Set<UUID> excludedApprovers) {
        public Approvable {
            Objects.requireNonNull(recordType, "recordType");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(content, "content");   // may hold null values, so not Map.copyOf
            excludedApprovers = Set.copyOf(excludedApprovers);
        }
    }

    /** One signature given; {@code complete} when enough signatures are collected. */
    public record Step(Signature signature, int collected, int required) {
        public boolean complete() {
            return collected >= required;
        }
    }

    /** Where a record stands: what is required and how many valid approval signatures it has. */
    public record Progress(ApprovalLevel level, int required, int collected) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public Step approve(Approvable record, @Nullable String comment, Supplier<ReauthEvidence> reauthentication) {
        ApprovalLevel level = policy.levelFor(record.kind(), record.siteId());
        if (level == ApprovalLevel.NONE) {
            throw new ApiException(409, "ARQ-STATE-002",
                    "No approval is configured for this kind of record; submitting makes it effective", List.of());
        }
        UUID me = CurrentUserHolder.require().userId();
        if (record.excludedApprovers().contains(me)) {
            throw ApiException.segregationOfDuties();
        }
        List<Signature> valid = validApprovals(record.recordType(), record.id());
        if (valid.stream().anyMatch(s -> s.signerUserId().equals(me))) {
            throw ApiException.alreadySigned();
        }
        int required = level.signaturesRequired();
        if (valid.size() >= required) {
            throw ApiException.invalidState("APPROVED", "APPROVE");
        }
        String meaning = valid.size() + 1 < required ? MEANING_REVIEWED : MEANING_APPROVED;

        ReauthEvidence evidence = reauthentication.get();
        Signature signature = signatures.sign(record.recordType(), record.id(), record.version(),
                record.businessKey(), record.siteId(), record.content(), meaning, comment, evidence);
        return new Step(signature, valid.size() + 1, required);
    }

    /** The configured level for a record kind (e.g. to skip approval on submit when NONE). */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public ApprovalLevel levelFor(String kind, @Nullable UUID siteId) {
        return policy.levelFor(kind, siteId);
    }

    @Transactional(readOnly = true)
    public Progress progress(String recordType, String kind, UUID id, @Nullable UUID siteId) {
        ApprovalLevel level = policy.levelFor(kind, siteId);
        return new Progress(level, level.signaturesRequired(), validApprovals(recordType, id).size());
    }

    private List<Signature> validApprovals(String recordType, UUID id) {
        return signatures.signaturesOf(recordType, id).stream()
                .filter(s -> "VALID".equals(s.status()))
                .filter(s -> MEANING_REVIEWED.equals(s.meaning()) || MEANING_APPROVED.equals(s.meaning()))
                .toList();
    }
}
