package com.arrheniq.kernel.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * Who performed an audited action. {@code name} is the printed name at the time of the action
 * (kept even if the user is later renamed). {@code userId} is null only for SYSTEM/PLATFORM actors.
 */
public record AuditActor(UUID userId, String name, Type type) {

    public enum Type { USER, API_KEY, SYSTEM, MIGRATION, PLATFORM }

    public AuditActor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (userId == null && (type == Type.USER || type == Type.API_KEY)) {
            throw new IllegalArgumentException("USER and API_KEY actors need an id");
        }
    }

    public static AuditActor system(String jobName) {
        return new AuditActor(null, "system:" + jobName, Type.SYSTEM);
    }
}
