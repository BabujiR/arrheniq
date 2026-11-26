package com.arrheniq.masterdata;

import com.fasterxml.jackson.annotation.JsonRawValue;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * One value of a controlled list.
 *
 * @param siteId     null: available at every site; otherwise only at that site (e.g. storage locations)
 * @param attributes JSON object, e.g. {@code {"temp_c":25,"rh":60,"role":"LONG_TERM"}} for a storage condition
 * @param fromCatalog shipped with ArrheniQ (e.g. the ICH conditions): can be retired, never deleted
 * @param inUse      some record stores this code ({@link DictionaryUsage}): can be retired, never deleted
 */
public record DictionaryValue(UUID id, long version, String dictionaryCode, String code, String label,
                              @Nullable String description, int sortOrder, @Nullable UUID siteId,
                              @JsonRawValue String attributes, String status, boolean fromCatalog, boolean inUse,
                              Instant createdAt, @Nullable Instant updatedAt) {

    DictionaryValue withUsage(boolean used) {
        return new DictionaryValue(id, version, dictionaryCode, code, label, description, sortOrder, siteId,
                attributes, status, fromCatalog, used, createdAt, updatedAt);
    }
}
