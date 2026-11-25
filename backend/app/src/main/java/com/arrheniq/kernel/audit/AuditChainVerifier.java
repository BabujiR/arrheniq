package com.arrheniq.kernel.audit;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Optional;

/**
 * Recomputes a tenant's hash chain and reports the first inconsistency
 * (audit-esignature.md §8; URS-AUD-003). Pure logic: the caller streams events in seq order
 * (from the database, an export, or an inspector's copy) so the check can run anywhere.
 */
public final class AuditChainVerifier {

    /** Outcome of a verification run. {@code firstBrokenSeq} is empty when the chain is intact. */
    public record Result(long eventsChecked, Optional<Long> firstBrokenSeq, String detail, byte[] lastHash) {

        public Result {
            lastHash = lastHash == null ? null : lastHash.clone();
        }

        @Override
        public byte[] lastHash() {
            return lastHash == null ? null : lastHash.clone();
        }

        public boolean intact() {
            return firstBrokenSeq.isEmpty();
        }

        public String lastHashHex() {
            return lastHash == null ? null : HexFormat.of().formatHex(lastHash);
        }
    }

    private AuditChainVerifier() { }

    /**
     * Verifies events starting at seq 1. A provisioned tenant always has a genesis event, so an
     * empty chain is reported as broken (it means the trail was lost or the wrong tenant was read).
     */
    public static Result verifyFromGenesis(Iterator<AuditEvent> events) {
        if (!events.hasNext()) {
            return new Result(0, Optional.of(1L), "no genesis event", AuditHasher.zeroHash());
        }
        return verify(events, 1, AuditHasher.zeroHash());
    }

    /**
     * Verifies a contiguous range starting at {@code expectedFirstSeq}, given the trusted hash of the
     * event just before it (for seq 1: 32 zero bytes; otherwise e.g. a WORM anchor).
     */
    public static Result verify(Iterator<AuditEvent> events, long expectedFirstSeq, byte[] trustedPrevHash) {
        long expectedSeq = expectedFirstSeq;
        byte[] prev = trustedPrevHash.clone();
        long checked = 0;
        while (events.hasNext()) {
            AuditEvent e = events.next();
            if (e.seq() != expectedSeq) {
                return new Result(checked, Optional.of(expectedSeq), "gap or reordering: expected seq " + expectedSeq
                        + " but found " + e.seq(), prev);
            }
            if (!Arrays.equals(e.prevHash(), prev)) {
                return new Result(checked, Optional.of(e.seq()), "prev_hash does not match predecessor", prev);
            }
            byte[] expected = AuditHasher.expectedHash(e, prev);
            if (!Arrays.equals(e.hash(), expected)) {
                return new Result(checked, Optional.of(e.seq()), "stored hash does not match recomputed content hash",
                        prev);
            }
            prev = expected;
            expectedSeq++;
            checked++;
        }
        return new Result(checked, Optional.empty(), "intact", prev);
    }
}
