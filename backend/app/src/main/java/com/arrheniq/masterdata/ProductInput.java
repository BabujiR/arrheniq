package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Create/update request for a product (URS-MD-002; legacy EA_PRD_PRODUCTS). The expiration period is
 * structured (value + unit) instead of the legacy free text (BR-007).
 */
public record ProductInput(
        @Nullable UUID siteId,
        @Nullable String code,
        @Nullable String name,
        @Nullable String description,
        @Nullable String productType,
        @Nullable String dosageForm,
        @Nullable String strength,
        @Nullable String expirationStatus,
        @Nullable Integer expirationValue,
        @Nullable String expirationUnit,
        @Nullable Boolean controlledSubstance) {

    record Clean(UUID siteId, String code, String name, @Nullable String description, String productType,
                 @Nullable String dosageForm, @Nullable String strength, String expirationStatus,
                 @Nullable Integer expirationValue, @Nullable String expirationUnit, boolean controlledSubstance) { }
}
