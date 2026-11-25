package com.arrheniq.kernel.audit;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldDiffAndReasonPolicyTest {

    @Test
    @Requirement("URS-AUD-002")
    void diffListsOnlyChangedBusinessFieldsSorted() {
        Map<String, Object> before = new HashMap<>();
        before.put("name", "Metformin 500");
        before.put("upperLimit", new BigDecimal("105.0"));
        before.put("version", 3L);
        before.put("strength", "500 mg");
        Map<String, Object> after = new HashMap<>(before);
        after.put("name", "Metformin HCl ER 500 mg");
        after.put("upperLimit", new BigDecimal("105"));   // same value, different scale
        after.put("version", 4L);                          // technical field
        after.put("dosageForm", "ER_TABLET");              // new field
        List<FieldChange> changes = FieldDiff.between(before, after);
        assertEquals(2, changes.size());
        assertEquals("dosageForm", changes.get(0).field());
        assertNull(changes.get(0).oldValue());
        assertEquals("name", changes.get(1).field());
    }

    @Test
    @Requirement("URS-AUD-002")
    void creationListsAllBusinessFields() {
        Map<String, Object> after = Map.of("code", "P-001", "id", UUID.randomUUID());
        List<FieldChange> changes = FieldDiff.between(null, after);
        assertEquals(1, changes.size());
        assertEquals("code", changes.get(0).field());
    }

    @Test
    @Requirement("URS-AUD-004")
    void reasonRequiredForChangesToLockedRecords() {
        ReasonPolicy policy = ReasonPolicy.defaults();
        AuditEventDraft approvedEdit = draft("UPDATE", "APPROVED", null);
        assertTrue(policy.requiresReason(approvedEdit));
        assertThrows(ReasonRequiredException.class, () -> policy.check(approvedEdit));
        policy.check(draft("UPDATE", "APPROVED", "Correction from review"));   // ok with reason
    }

    @Test
    @Requirement("URS-AUD-004")
    void reasonRequiredForDeletionAndReversalsWhateverTheStatus() {
        ReasonPolicy policy = ReasonPolicy.defaults();
        assertTrue(policy.requiresReason(draft("LOGICAL_DELETE", "DRAFT", null)));
        assertTrue(policy.requiresReason(draft("REVERSAL", null, null)));
        assertTrue(policy.requiresReason(draft("SCHEDULE_DATE_CHANGE", "SCHEDULED", null)));
    }

    @Test
    @Requirement("URS-AUD-004")
    void draftsAndCreationDoNotNeedReasonsByDefault() {
        ReasonPolicy policy = ReasonPolicy.defaults();
        assertFalse(policy.requiresReason(draft("UPDATE", "DRAFT", null)));
        assertFalse(policy.requiresReason(draft("CREATE", null, null)));
    }

    @Test
    @Requirement("URS-AUD-004")
    void tenantCanRequireReasonsForMoreActions() {
        ReasonPolicy strict = new ReasonPolicy(Set.of("UPDATE"));
        assertTrue(strict.requiresReason(draft("UPDATE", "DRAFT", null)));
    }

    @Test
    void blankReasonCountsAsMissing() {
        assertNull(draft("UPDATE", "APPROVED", "   ").reason());
    }

    private static AuditEventDraft draft(String action, String statusBefore, String reason) {
        return new AuditEventDraft(action, "PRODUCT", UUID.randomUUID(), 1L, "P-001", null,
                List.of(new FieldChange("name", "a", "b")), statusBefore, reason, null);
    }
}
