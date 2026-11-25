package com.arrheniq.kernel.audit;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hash chain construction and tamper detection (URS-AUD-001/003; audit-esignature.md §8, §10). */
@Requirement({"URS-AUD-001", "URS-AUD-003"})
class AuditChainTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant T0 = Instant.parse("2026-11-25T18:00:00.000001Z");

    /** Golden vector: must equal PostgreSQL digest() of the same text (checked in the DB cross-check). */
    @Test
    void genesisHashMatchesGoldenVector() {
        String hex = HexFormat.of().formatHex(AuditHasher.genesisHash(TENANT, T0));
        assertEquals(GoldenVectors.GENESIS_HASH_HEX, hex);
    }

    @Test
    void intactChainVerifies() {
        List<AuditEvent> chain = chain(5);
        AuditChainVerifier.Result r = AuditChainVerifier.verifyFromGenesis(chain.iterator());
        assertTrue(r.intact(), r.detail());
        assertEquals(5L, r.eventsChecked());
    }

    @Test
    void contentTamperingIsDetectedAtTheTamperedEvent() {
        List<AuditEvent> chain = chain(5);
        AuditEvent original = chain.get(2);
        AuditEvent tampered = new AuditEvent(original.tenantId(), original.seq(), original.id(), original.occurredAt(),
                original.siteId(), original.actorUserId(), original.actorName(), original.actorType(),
                original.action(), original.recordType(), original.recordId(), original.recordVersion(),
                original.businessKey(), List.of(new FieldChange("assay", new BigDecimal("98.1"), new BigDecimal("99.9"))),
                original.reason(), original.reasonCode(), original.correlationId(), original.legacy(),
                original.prevHash(), original.hash());
        chain.set(2, tampered);
        AuditChainVerifier.Result r = AuditChainVerifier.verifyFromGenesis(chain.iterator());
        assertFalse(r.intact());
        assertEquals(Long.valueOf(3), r.firstBrokenSeq().orElseThrow());
    }

    @Test
    void deletedEventIsDetectedAsGap() {
        List<AuditEvent> chain = chain(5);
        chain.remove(3);
        AuditChainVerifier.Result r = AuditChainVerifier.verifyFromGenesis(chain.iterator());
        assertFalse(r.intact());
        assertEquals(Long.valueOf(4), r.firstBrokenSeq().orElseThrow());
    }

    @Test
    void emptyChainIsNotIntact() {
        AuditChainVerifier.Result r = AuditChainVerifier.verifyFromGenesis(List.<AuditEvent>of().iterator());
        assertFalse(r.intact());
        assertEquals(Long.valueOf(1), r.firstBrokenSeq().orElseThrow());
    }

    /** PostgreSQL jsonb::text output (reordered keys, spaces, unicode) must hash like the original. */
    @Test
    void postgresJsonbRenderingHashesLikeTheOriginal() {
        AuditEvent e = chain(2).get(1);
        String pgRendered = "[{\"new\": \"New 2\", \"old\": \"Old 2\", \"field\": \"name\"}, "
                + "{\"new\": 24.0, \"old\": 2, \"field\": \"expirationValue\"}]";
        AuditEvent reread = new AuditEvent(e.tenantId(), e.seq(), e.id(), e.occurredAt(), e.siteId(), e.actorUserId(),
                e.actorName(), e.actorType(), e.action(), e.recordType(), e.recordId(), e.recordVersion(),
                e.businessKey(), ChangesCodec.parse(pgRendered), e.reason(), e.reasonCode(), e.correlationId(),
                e.legacy(), e.prevHash(), e.hash());
        assertTrue(AuditChainVerifier.verify(List.of(reread).iterator(), 2, e.prevHash()).intact());
    }

    @Test
    void storedChangesSurviveJsonRoundTripWithSameHash() {
        AuditEvent e = chain(2).get(1);
        List<FieldChange> roundTripped = ChangesCodec.parse(ChangesCodec.toJson(e.changes()));
        AuditEvent reread = new AuditEvent(e.tenantId(), e.seq(), e.id(), e.occurredAt(), e.siteId(), e.actorUserId(),
                e.actorName(), e.actorType(), e.action(), e.recordType(), e.recordId(), e.recordVersion(),
                e.businessKey(), roundTripped, e.reason(), e.reasonCode(), e.correlationId(), e.legacy(),
                e.prevHash(), e.hash());
        assertEquals(HexFormat.of().formatHex(e.hash()),
                HexFormat.of().formatHex(AuditHasher.hash(reread, e.prevHash())));
    }

    /** Builds a valid chain: genesis + (n-1) product updates. */
    static List<AuditEvent> chain(int n) {
        List<AuditEvent> list = new ArrayList<>();
        byte[] zero = AuditHasher.zeroHash();
        AuditEvent genesis = new AuditEvent(TENANT, 1, UUID.randomUUID(), T0, null, null, "ops@arrheniq",
                AuditActor.Type.PLATFORM, AuditHasher.GENESIS_ACTION, "TENANT", TENANT, null, "tenant-a",
                List.of(), null, null, "provision", false, zero, AuditHasher.genesisHash(TENANT, T0));
        list.add(genesis);
        byte[] prev = genesis.hash();
        UUID product = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        for (int i = 2; i <= n; i++) {
            AuditEvent e = new AuditEvent(TENANT, i, UUID.randomUUID(), T0.plusSeconds(i), null, user, "Sam Manager",
                    AuditActor.Type.USER, "UPDATE", "PRODUCT", product, (long) i, "P-001",
                    List.of(new FieldChange("name", "Old " + i, "New " + i),
                            new FieldChange("expirationValue", (long) i, new BigDecimal("24.0"))),
                    "Correction from review", "REVIEW_COMMENT", "corr-" + i, false, null, null);
            byte[] h = AuditHasher.hash(e, prev);
            e = e.withHashes(prev, h);
            list.add(e);
            prev = h;
        }
        return list;
    }
}
