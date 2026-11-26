package com.arrheniq.masterdata;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductRulesAndLifecycleTest {

    private static final UUID SITE = UUID.randomUUID();

    static ProductInput product(String expStatus, Integer value, String unit) {
        return new ProductInput(SITE, "met-500", "Metformin HCl ER 500 mg", null, "drug_product", "ER tablet",
                "500 mg", expStatus, value, unit, null);
    }

    @Test
    @Requirement("URS-MD-002")
    void expirationIsStructured() {
        ProductInput.Clean c = ProductRules.validate(product(null, 24, "month"));
        assertEquals("ESTABLISHED", c.expirationStatus());
        assertEquals("MONTH", c.expirationUnit());
        assertEquals("MET-500", c.code());
        assertEquals("DRUG_PRODUCT", c.productType());

        List<String> missing = TestMethodRulesTest.codes(() -> ProductRules.validate(product("ESTABLISHED", null, null)));
        assertTrue(missing.containsAll(List.of("expirationValue:REQUIRED", "expirationUnit:INVALID_VALUE")),
                missing.toString());

        List<String> extra = TestMethodRulesTest.codes(() -> ProductRules.validate(product("TO_BE_DETERMINED", 24, "MONTH")));
        assertTrue(extra.containsAll(List.of("expirationValue:NOT_ALLOWED", "expirationUnit:NOT_ALLOWED")),
                extra.toString());
    }

    @Test
    @Requirement("URS-MD-002")
    void siteAndTypeAreRequired() {
        ProductInput in = new ProductInput(null, "P1", "P", null, "CAPSULE", null, null, "NOT_APPLICABLE", null, null,
                null);
        List<String> errors = TestMethodRulesTest.codes(() -> ProductRules.validate(in));
        assertTrue(errors.containsAll(List.of("siteId:REQUIRED", "productType:INVALID_VALUE")), errors.toString());
    }

    @Test
    void lifecycleAllowsOnlyTheDefinedTransitions() {
        assertEquals("IN_REVIEW", ProductLifecycle.next("DRAFT", ProductLifecycle.Action.SUBMIT));
        assertEquals("APPROVED", ProductLifecycle.next("IN_REVIEW", ProductLifecycle.Action.APPROVE));
        assertEquals("DRAFT", ProductLifecycle.next("IN_REVIEW", ProductLifecycle.Action.REJECT));
        assertEquals("DRAFT", ProductLifecycle.next("APPROVED", ProductLifecycle.Action.EDIT));
        assertEquals("RETIRED", ProductLifecycle.next("APPROVED", ProductLifecycle.Action.RETIRE));

        ApiException e = assertThrows(ApiException.class,
                () -> ProductLifecycle.next("DRAFT", ProductLifecycle.Action.APPROVE));
        assertEquals(409, e.status());
        assertEquals("ARQ-STATE-001", e.code());
        assertThrows(ApiException.class, () -> ProductLifecycle.next("IN_REVIEW", ProductLifecycle.Action.EDIT));
        assertThrows(ApiException.class, () -> ProductLifecycle.next("RETIRED", ProductLifecycle.Action.EDIT));
        assertThrows(ApiException.class, () -> ProductLifecycle.next("RETIRED", ProductLifecycle.Action.RETIRE));
    }

    @Test
    @Requirement("URS-ESG-003")
    void signedContentIgnoresWorkflowFieldsButNotBusinessChanges() {
        UUID id = UUID.randomUUID();
        ProductInput.Clean c = ProductRules.validate(product(null, 24, "MONTH"));
        byte[] reviewed = com.arrheniq.kernel.esign.RecordHasher.contentHash(Product.businessContent(id, c));
        byte[] again = com.arrheniq.kernel.esign.RecordHasher.contentHash(Product.businessContent(id,
                ProductRules.validate(product(null, 24, "MONTH"))));
        assertTrue(java.util.Arrays.equals(reviewed, again), "same content, same hash");
        byte[] changed = com.arrheniq.kernel.esign.RecordHasher.contentHash(Product.businessContent(id,
                ProductRules.validate(product(null, 36, "MONTH"))));
        assertFalse(java.util.Arrays.equals(reviewed, changed), "a business change must change the hash");
        assertFalse(Product.businessContent(id, c).containsKey("status"));
    }
}
