package com.arrheniq.migrator.transform;

import com.arrheniq.migrator.transform.ParameterMapper.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** URS-ADM-001, URS-SEC-003, URS-MIG-001; legacy-mapping.md §6. */
class ParameterMapperTest {

    private final ParameterMapper mapper = new ParameterMapper();

    @Test
    void secretsAreNeverMigrated() {
        var m = mapper.map("EMAIL ADMIN PASSWORD", "password");
        assertEquals(Outcome.SECRET_DISCARDED, m.outcome());
        assertNull(m.setting());
        assertEquals(Outcome.SECRET_DISCARDED, mapper.map("DATA_LOCK_PASSWORD", "x").outcome());
    }

    @Test
    void approvalLevels() {
        assertEquals("\"DOUBLE\"", mapper.map("DATA APPROVAL LEVEL", "D").setting().jsonValue());
        assertEquals("approval.specification", mapper.map("SPECIFICATION APPROVAL LEVEL", "S").setting().key());
        assertEquals(Outcome.UNKNOWN, mapper.map("DATA APPROVAL LEVEL", "X").outcome());
    }

    @Test
    void weekendRules() {
        assertEquals("\"MOVE_BEFORE\"", mapper.map("MOVE SUNDAY PULL DATE", "F").setting().jsonValue());
        assertEquals("\"MOVE_AFTER\"", mapper.map("MOVE SATURDAY PULL DATE", "m").setting().jsonValue());
        assertEquals("\"LEAVE\"", mapper.map("MOVE SATURDAY PULL DATE", "L").setting().jsonValue());
    }

    @Test
    void numbersAndFlags() {
        assertEquals("3", mapper.map("INVENTORY PULL DAYS BUFFER", "3").setting().jsonValue());
        assertEquals("true", mapper.map("INVENTORY EXTRAS ALLOWED", "Y").setting().jsonValue());
        assertEquals(Outcome.UNKNOWN, mapper.map("SYSTEM LOCK OUT", "thirty").outcome());
    }

    @Test
    void idpPoliciesAreReportedNotMigrated() {
        assertEquals(Outcome.NOT_MIGRATED, mapper.map("INVALID LOGIN COUNT", "4").outcome());
    }

    @Test
    void companyNameIsJsonEscaped() {
        assertEquals("\"Acme \\\"Pharma\\\"\"", mapper.map("COMPANY NAME", "Acme \"Pharma\"").setting().jsonValue());
    }
}
