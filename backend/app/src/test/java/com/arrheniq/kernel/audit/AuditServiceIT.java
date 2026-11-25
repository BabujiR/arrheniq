package com.arrheniq.kernel.audit;

import com.arrheniq.kernel.tenancy.TenantContext;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import com.arrheniq.testsupport.PostgresIntegrationTest;
import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The audit kernel against real PostgreSQL: Java hashing + DB chain trigger + RLS + append-only.
 * Traces: URS-AUD-001..004, URS-TEN-001; audit-esignature.md §3–§5, §8.
 */
@Requirement({"URS-AUD-001", "URS-AUD-002", "URS-AUD-003", "URS-AUD-004", "URS-TEN-001"})
class AuditServiceIT extends PostgresIntegrationTest {

    private static final AuditActor SAM = new AuditActor(UUID.randomUUID(), "Sam Manager", AuditActor.Type.USER);

    @Autowired
    private AuditService audit;

    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate tx;
    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() throws SQLException {
        tx = new TransactionTemplate(txManager);
        tenantA = provisionTenant("audit-a");
        tenantB = provisionTenant("audit-b");
    }

    @Test
    void appendedEventsFormAVerifiableChainFromTheDatabaseGenesis() {
        inTenant(tenantA, () -> tx.execute(s -> {
            audit.open("product.update");
            audit.record(SAM, productUpdate(null, "Metformin HCl ER 500 mg — \"Ü\""));
            audit.record(SAM, productUpdate(null, "Metformin HCl ER 750 mg"));
            return null;
        }));
        List<AuditEvent> chain = inTenant(tenantA, () -> audit.loadChain(1, 1000));
        assertEquals(3, chain.size());
        assertEquals(AuditHasher.GENESIS_ACTION, chain.get(0).action());
        assertEquals(chain.get(1).correlationId(), chain.get(2).correlationId());
        assertTrue(chain.get(1).occurredAt().compareTo(chain.get(2).occurredAt()) <= 0);
        AuditChainVerifier.Result r = AuditChainVerifier.verifyFromGenesis(chain.iterator());
        assertTrue(r.intact(), r.detail());
    }

    @Test
    void tenantsHaveSeparateChainsAndCannotSeeEachOther() {
        inTenant(tenantA, () -> tx.execute(s -> {
            audit.open("product.update");
            return audit.record(SAM, productUpdate(null, "A"));
        }));
        List<AuditEvent> chainB = inTenant(tenantB, () -> audit.loadChain(1, 1000));
        assertEquals(1, chainB.size(), "tenant B sees only its own genesis");
        assertEquals(tenantB, chainB.get(0).tenantId());
    }

    @Test
    void recordingRequiresATransaction() {
        assertThrows(IllegalTransactionStateException.class,
                () -> inTenant(tenantA, () -> audit.record(SAM, productUpdate(null, "x"))));
    }

    @Test
    void recordingRequiresAnOpenedAuditContext() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> inTenant(tenantA,
                () -> tx.execute(s -> audit.record(SAM, productUpdate(null, "x")))));
        assertTrue(e.getMessage().contains("ARQ-AUDIT-011"), e.getMessage());
    }

    @Test
    void missingReasonOnLockedRecordIsRejectedAndNothingIsWritten() {
        assertThrows(ReasonRequiredException.class, () -> inTenant(tenantA, () -> tx.execute(s -> {
            audit.open("product.update");
            return audit.record(SAM, new AuditEventDraft("UPDATE", "PRODUCT", UUID.randomUUID(), 2L, "P-001", null,
                    List.of(new FieldChange("name", "a", "b")), "APPROVED", null, null));
        })));
        assertEquals(1, inTenant(tenantA, () -> audit.loadChain(1, 1000)).size());
    }

    @Test
    void storedEventsCannotBeChangedOrDeletedEvenByTheSchemaOwner() throws SQLException {
        try (Connection c = ownerConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.tenant_id', '" + tenantA + "', false)");
            SQLException upd = assertThrows(SQLException.class,
                    () -> s.execute("UPDATE audit.audit_event SET reason = 'x' WHERE seq = 1"));
            assertTrue(upd.getMessage().contains("ARQ-AUDIT-002"), upd.getMessage());
            SQLException del = assertThrows(SQLException.class,
                    () -> s.execute("DELETE FROM audit.audit_event WHERE seq = 1"));
            assertTrue(del.getMessage().contains("ARQ-AUDIT-002"), del.getMessage());
        }
    }

    @Test
    void concurrentAppendsStayContiguous() throws Exception {
        int writers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                String name = "Concurrent " + i;
                futures.add(pool.submit(() -> inTenant(tenantA, () -> tx.execute(s -> {
                    audit.open("product.update");
                    return audit.record(SAM, productUpdate(null, name));
                }))));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        List<AuditEvent> chain = inTenant(tenantA, () -> audit.loadChain(1, 1000));
        assertEquals(writers + 1, chain.size());
        AuditChainVerifier.Result r = AuditChainVerifier.verifyFromGenesis(chain.iterator());
        assertTrue(r.intact(), r.detail());
        for (int i = 1; i < chain.size(); i++) {
            assertTrue(chain.get(i - 1).occurredAt().compareTo(chain.get(i).occurredAt()) <= 0,
                    "occurred_at must follow seq order");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static AuditEventDraft productUpdate(String reason, String newName) {
        return new AuditEventDraft("UPDATE", "PRODUCT", UUID.randomUUID(), 2L, "P-001", null,
                List.of(new FieldChange("name", "Metformin 500", newName),
                        new FieldChange("upperLimit", new BigDecimal("105.00"), new BigDecimal("104.5"))),
                "DRAFT", reason, null);
    }

    private static <T> T inTenant(UUID tenant, Supplier<T> work) {
        TenantContext ctx = new TenantContext(tenant, Optional.of(SAM.userId()), true, Set.of());
        List<T> out = new ArrayList<>(1);
        TenantContextHolder.runAs(ctx, () -> out.add(work.get()));
        return out.get(0);
    }
}
