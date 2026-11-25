package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A product as returned by the API. */
public record Product(UUID id, long version, UUID siteId, String code, String name, @Nullable String description,
                      String productType, @Nullable String dosageForm, @Nullable String strength,
                      String expirationStatus, @Nullable Integer expirationValue, @Nullable String expirationUnit,
                      boolean controlledSubstance, String status, @Nullable UUID createdBy, @Nullable UUID updatedBy,
                      Instant createdAt, @Nullable Instant updatedAt) {

    ProductInput.Clean clean() {
        return new ProductInput.Clean(siteId, code, name, description, productType, dosageForm, strength,
                expirationStatus, expirationValue, expirationUnit, controlledSubstance);
    }

    /** What a signature is bound to: business content only (no status/version/timestamps), see RecordHasher. */
    Map<String, @Nullable Object> businessContent() {
        return businessContent(id, clean());
    }

    static Map<String, @Nullable Object> businessContent(UUID id, ProductInput.Clean c) {
        Map<String, @Nullable Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("siteId", c.siteId());
        m.put("code", c.code());
        m.put("name", c.name());
        m.put("description", c.description());
        m.put("productType", c.productType());
        m.put("dosageForm", c.dosageForm());
        m.put("strength", c.strength());
        m.put("expirationStatus", c.expirationStatus());
        m.put("expirationValue", c.expirationValue() == null ? null : (long) c.expirationValue());
        m.put("expirationUnit", c.expirationUnit());
        m.put("controlledSubstance", c.controlledSubstance());
        return m;
    }

    /** Fields compared for the audit trail (business content + status). */
    static Map<String, @Nullable Object> auditMap(UUID id, ProductInput.Clean c, String status) {
        Map<String, @Nullable Object> m = businessContent(id, c);
        m.remove("id");
        m.put("status", status);
        return m;
    }
}
