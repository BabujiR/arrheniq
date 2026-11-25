package com.arrheniq.kernel.security;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Requirement({"URS-IAM-005", "URS-TEN-002"})
class AccessGrantsTest {

    private static final UUID NJ = UUID.randomUUID();
    private static final UUID NC = UUID.randomUUID();

    @Test
    void permissionAppliesOnlyAtAssignedSites() {
        AccessGrants g = AccessGrants.from(List.of(new AccessGrants.Grant("result.approve.final", NJ)));
        assertTrue(g.has("result.approve.final"));
        assertTrue(g.hasAt("result.approve.final", NJ));
        assertFalse(g.hasAt("result.approve.final", NC));
        assertFalse(g.has("study.create"));
    }

    @Test
    void allSitesAssignmentWins() {
        AccessGrants g = AccessGrants.from(List.of(
                new AccessGrants.Grant("study.view", NJ), new AccessGrants.Grant("study.view", null)));
        assertTrue(g.scopeOf("study.view").allSites());
        assertTrue(g.hasAt("study.view", NC));
    }

    @Test
    void visibleSitesIsTheUnionOfAllPermissions() {
        AccessGrants g = AccessGrants.from(List.of(
                new AccessGrants.Grant("study.view", NJ), new AccessGrants.Grant("result.view", NC)));
        assertEquals(Set.of(NJ, NC), g.visibleSites().siteIds());
        assertFalse(g.visibleSites().allSites());
    }

    @Test
    void noAssignmentsMeansNoAccess() {
        AccessGrants g = AccessGrants.from(List.of(new AccessGrants.Grant(null, null)));
        assertTrue(g.visibleSites().isEmpty());
        assertTrue(g.permissions().isEmpty());
        assertFalse(g.hasAt("study.view", null));
    }
}
