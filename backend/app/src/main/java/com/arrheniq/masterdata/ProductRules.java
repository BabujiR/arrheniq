package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/** Validation of products (URS-MD-002). Mirrors the CHECK constraints of {@code app.product} (V4). */
final class ProductRules {

    static final Set<String> TYPES = Set.of("DRUG_SUBSTANCE", "DRUG_PRODUCT", "BIOLOGIC_DS", "BIOLOGIC_DP",
            "EXCIPIENT", "OTHER");
    static final Set<String> EXPIRATION_STATUSES = Set.of("ESTABLISHED", "TO_BE_DETERMINED", "NOT_APPLICABLE");
    static final Set<String> EXPIRATION_UNITS = Set.of("DAY", "WEEK", "MONTH");

    private ProductRules() { }

    static ProductInput.Clean validate(ProductInput in) {
        Validation v = new Validation();
        if (in.siteId() == null) {
            v.add("siteId", "REQUIRED", null);
        }
        String code = Validation.normalizeCode(in.code());
        v.code("code", code);
        String name = Validation.trimToNull(in.name());
        v.required("name", name, 200);
        String description = Validation.trimToNull(in.description());
        v.maxLength("description", description, 2000);
        String type = upper(in.productType());
        v.oneOf("productType", type, TYPES);
        String dosageForm = Validation.trimToNull(in.dosageForm());
        v.maxLength("dosageForm", dosageForm, 100);
        String strength = Validation.trimToNull(in.strength());
        v.maxLength("strength", strength, 100);

        String expStatus = in.expirationStatus() == null ? "ESTABLISHED" : upper(in.expirationStatus());
        v.oneOf("expirationStatus", expStatus, EXPIRATION_STATUSES);
        String expUnit = upper(in.expirationUnit());
        if ("ESTABLISHED".equals(expStatus)) {
            if (in.expirationValue() == null) {
                v.add("expirationValue", "REQUIRED", "an established expiration period needs a value");
            }
            v.range("expirationValue", in.expirationValue(), 1, 1200);
            v.oneOf("expirationUnit", expUnit, EXPIRATION_UNITS);
        } else {
            String why = "only an ESTABLISHED expiration has a value and unit";
            v.absent("expirationValue", in.expirationValue(), why);
            v.absent("expirationUnit", expUnit, why);
        }
        v.throwIfAny();
        return new ProductInput.Clean(in.siteId(), code, name, description, type, dosageForm, strength, expStatus,
                in.expirationValue(), expUnit, Boolean.TRUE.equals(in.controlledSubstance()));
    }

    private static @Nullable String upper(@Nullable String s) {
        String t = Validation.trimToNull(s);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }
}
