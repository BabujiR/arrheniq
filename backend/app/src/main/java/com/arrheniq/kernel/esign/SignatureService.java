package com.arrheniq.kernel.esign;

import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.AuditEventDraft;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.audit.FieldChange;
import com.arrheniq.kernel.security.ArrheniqUser;
import com.arrheniq.kernel.security.CurrentUser;
import com.arrheniq.kernel.security.CurrentUserHolder;
import com.arrheniq.kernel.security.ReauthEvidence;
import com.arrheniq.kernel.security.ReauthRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Electronic signatures (audit-esignature.md §9; URS-ESG-001..003; Part 11 §11.50, §11.70, §11.200).
 * A signature is written in the same transaction as the state change it authorises, together with a
 * SIGN audit event. It needs fresh re-authentication evidence of the signer ({@code ReauthGuard}).
 */
@Service
public class SignatureService {

    private final JdbcClient jdbc;
    private final AuditService audit;

    public SignatureService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /**
     * Signs {@code businessContent} of the record at {@code recordVersion} with {@code meaning}
     * (e.g. APPROVED) for the current user.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Signature sign(String recordType, UUID recordId, long recordVersion, @Nullable String businessKey,
                          @Nullable UUID siteId, Map<String, ? extends @Nullable Object> businessContent,
                          String meaning, @Nullable String comment, ReauthEvidence evidence) {
        CurrentUser signer = CurrentUserHolder.require();
        if (!evidence.userId().equals(signer.userId()) || evidence.purpose() != ReauthRequest.Purpose.SIGN) {
            throw new IllegalStateException("ARQ-ESG-002: re-authentication evidence does not belong to the signer");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ArrheniqUser principal)) {
            throw new IllegalStateException("ARQ-ESG-003: signing needs an interactive user session");
        }
        String issuer = principal.getIdToken().getIssuer().toString();
        String acr = principal.getIdToken().getAuthenticationContextClass();
        byte[] contentHash = RecordHasher.contentHash(businessContent);
        UUID id = UUID.randomUUID();
        String trimmedComment = comment == null || comment.isBlank() ? null : comment.trim();

        OffsetDateTime signedAt = jdbc.sql("""
                INSERT INTO audit.e_signature (tenant_id, id, record_type, record_id, record_version, content_hash,
                                               meaning, signer_user_id, signer_name, signed_at, auth_time,
                                               idp_issuer, idp_acr, comment)
                VALUES (app.current_tenant(), ?, ?, ?, ?, ?, ?, ?, ?, clock_timestamp(), ?, ?, ?, ?)
                RETURNING signed_at
                """)
            .params(id, recordType, recordId, recordVersion, contentHash, meaning, signer.userId(),
                    signer.displayName(), OffsetDateTime.ofInstant(evidence.authTime(), ZoneOffset.UTC),
                    issuer, acr, trimmedComment)
            .query(OffsetDateTime.class)
            .single();

        audit.record(new AuditActor(signer.userId(), signer.displayName(), AuditActor.Type.USER),
                new AuditEventDraft("SIGN", recordType, recordId, recordVersion, businessKey, siteId,
                        List.of(new FieldChange("meaning", null, meaning),
                                new FieldChange("contentHash", null, HexFormat.of().formatHex(contentHash)),
                                new FieldChange("authTime", null, evidence.authTime()),
                                new FieldChange("signatureId", null, id)),
                        null, null, null));
        return new Signature(id, meaning, signer.userId(), signer.displayName(), signedAt.toInstant(), recordVersion,
                "VALID", trimmedComment);
    }

    /**
     * Marks every still-valid signature of the record SUPERSEDED (its content is about to change, §11.70).
     * Returns how many signatures were superseded.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int supersedeAll(String recordType, UUID recordId, String reason) {
        return jdbc.sql("""
                INSERT INTO audit.e_signature_status (tenant_id, id, signature_id, status, reason)
                SELECT s.tenant_id, gen_random_uuid(), s.id, 'SUPERSEDED', ?
                  FROM audit.e_signature s
                 WHERE s.record_type = ? AND s.record_id = ?
                   AND NOT EXISTS (SELECT 1 FROM audit.e_signature_status x
                                    WHERE x.tenant_id = s.tenant_id AND x.signature_id = s.id)
                """)
            .params(reason, recordType, recordId)
            .update();
    }

    /** Signatures of a record, newest first, with their current status. */
    @Transactional(readOnly = true)
    public List<Signature> signaturesOf(String recordType, UUID recordId) {
        return jdbc.sql("""
                SELECT s.id, s.meaning, s.signer_user_id, s.signer_name, s.signed_at, s.record_version, s.comment,
                       COALESCE((SELECT x.status FROM audit.e_signature_status x
                                  WHERE x.tenant_id = s.tenant_id AND x.signature_id = s.id
                                  ORDER BY x.occurred_at DESC LIMIT 1), 'VALID') AS status
                  FROM audit.e_signature s
                 WHERE s.record_type = ? AND s.record_id = ?
                 ORDER BY s.signed_at DESC
                """)
            .params(recordType, recordId)
            .query((rs, n) -> new Signature(rs.getObject("id", UUID.class), rs.getString("meaning"),
                    rs.getObject("signer_user_id", UUID.class), rs.getString("signer_name"),
                    rs.getObject("signed_at", OffsetDateTime.class).toInstant(), rs.getLong("record_version"),
                    rs.getString("status"), rs.getString("comment")))
            .list();
    }
}
