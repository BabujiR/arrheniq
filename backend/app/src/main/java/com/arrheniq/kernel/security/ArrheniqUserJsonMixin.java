package com.arrheniq.kernel.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * JSON shape of {@link ArrheniqUser} in the session ({@link SessionJson}): the class name (checked against the
 * allow-list) and its {@link ArrheniqUserSessionForm}, in which the token claims are plain JSON text. Spring
 * Security's own OIDC user mixin is not used for it: that one stores every claim value with its Java type, and a
 * claim type outside Spring's allow-list (e.g. an immutable {@code List.of} for {@code aud}) would make the whole
 * session unreadable.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
abstract class ArrheniqUserJsonMixin {

    @JsonValue
    abstract ArrheniqUserSessionForm sessionForm();

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static ArrheniqUser fromSessionForm(ArrheniqUserSessionForm form) {
        throw new UnsupportedOperationException("mixin only; Jackson calls ArrheniqUser.fromSessionForm");
    }
}
