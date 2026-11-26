package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;
import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.AuditEventDraft;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.audit.FieldChange;
import com.arrheniq.kernel.tenancy.TenantContext;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Writes security events (logins, denials, re-authentication, logout, session lock/expiry) into the
 * tenant's audit trail (security-architecture.md §7; URS-AUD-001). Each event commits in its own
 * transaction, so a denied login is recorded even though the login itself fails.
 */
@Component
public class SecurityAuditor {

    /** Record type used for authentication events. */
    public static final String RECORD_TYPE = "USER_SESSION";

    private final AuditService audit;
    private final TransactionTemplate requiresNew;

    public SecurityAuditor(AuditService audit, PlatformTransactionManager txManager) {
        this.audit = audit;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(UUID tenantId, AuditActor actor, String action, @Nullable UUID userId, @Nullable String businessKey,
                       List<FieldChange> details) {
        inTenantTransaction(tenantId, "security." + action.toLowerCase(Locale.ROOT), () -> {
            recordInCurrentTransaction(actor, action, userId, businessKey, details);
            return null;
        });
    }

    /** Appends an authentication event inside an already open tenant transaction (see {@link #inTenantTransaction}). */
    public void recordInCurrentTransaction(AuditActor actor, String action, @Nullable UUID userId,
                                           @Nullable String businessKey,
                                           List<FieldChange> details) {
        audit.record(actor, new AuditEventDraft(action, RECORD_TYPE, userId, null, businessKey, null,
                details, null, null, null));
    }

    /**
     * Runs {@code work} in a new transaction bound to the tenant (system context) with the audit
     * context opened, so it may write to GxP/SECURITY tables and append audit events.
     */
    public <T extends @Nullable Object> T inTenantTransaction(UUID tenantId, String operation, Supplier<T> work) {
        // the result is passed out through a holder: TransactionTemplate.execute is declared to return a nullable
        // value, which would make every caller's result nullable even when the work never returns null
        return TenantContextHolder.callAs(TenantContext.system(tenantId), () -> {
            List<T> result = new ArrayList<>(1);
            requiresNew.executeWithoutResult(_ -> {
                audit.open(operation);
                result.add(work.get());
            });
            return result.getFirst();
        });
    }

    public static AuditActor actorFor(ArrheniqUser user) {
        return new AuditActor(user.userId(), user.displayName(), AuditActor.Type.USER);
    }
}
