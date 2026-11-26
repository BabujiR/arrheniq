package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

/** Request bodies of the master-data API. {@code expectedVersion} is the version the user saw (optimistic locking). */
public final class Requests {

    private Requests() { }

    public record TestMethodUpdate(@Nullable Long expectedVersion, @Nullable String reason,
                                   @Nullable TestMethodInput data) { }

    public record ProductUpdate(@Nullable Long expectedVersion, @Nullable String reason,
                                @Nullable ProductInput data) { }

    public record DictionaryValueUpdate(@Nullable Long expectedVersion, @Nullable String reason,
                                        @Nullable DictionaryValueInput data) { }

    /** Submit / reject / retire: reason required for reject and retire (ReasonPolicy). */
    public record StatusChange(@Nullable Long expectedVersion, @Nullable String reason) { }

    /** Approve with e-signature (meaning APPROVED); needs fresh re-authentication (purpose SIGN). */
    public record Approve(@Nullable Long expectedVersion, @Nullable String comment) { }

    static long version(@Nullable Long v) {
        if (v == null || v < 0) {
            Validation val = new Validation();
            val.add("expectedVersion", "REQUIRED", "the version you loaded");
            val.throwIfAny();
        }
        return v == null ? 0 : v;
    }
}
