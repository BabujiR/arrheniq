package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Session attributes as JSON instead of Java serialization (ADR 0018 follow-up; security-architecture T16:
 * no native deserialization of stored data).
 * <p>
 * Format of one attribute: {@code <class name>\n<JSON>}. Only the classes in {@link #ALLOWED} may be stored or
 * read; nested polymorphic values (the authentication, its principal, ID-token claims) are typed and validated by
 * Spring Security's Jackson modules, extended with {@link ArrheniqUser}. Writing any other type fails loudly
 * (a developer added a session attribute without allow-listing it); reading one returns {@code null} and is
 * logged (a stored value that this version does not accept is treated as absent, never instantiated).
 */
public final class SessionJson {

    private static final Logger log = LoggerFactory.getLogger(SessionJson.class);

    /** Every type that may be the value of a session attribute. */
    static final Map<String, Class<?>> ALLOWED = Stream.of(
                    SecurityContextImpl.class,          // Spring Security: the signed-in user
                    OAuth2AuthorizationRequest.class,   // Spring Security: login in progress (state, PKCE verifier)
                    DefaultSavedRequest.class,          // Spring Security: request saved before login
                    ReauthRequest.class, ReauthEvidence.class,
                    Instant.class, Duration.class, Boolean.class, String.class)
            .collect(Collectors.toUnmodifiableMap(Class::getName, Function.identity()));

    private final JsonMapper mapper;

    public SessionJson(ClassLoader loader) {
        BasicPolymorphicTypeValidator.Builder types = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(ArrheniqUser.class);
        this.mapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(loader, types))
                .addMixIn(ArrheniqUser.class, ArrheniqUserJsonMixin.class)
                .build();
    }

    public byte[] write(Object value) {
        Class<?> type = ALLOWED.get(value.getClass().getName());
        if (type == null || type != value.getClass()) {
            throw new IllegalArgumentException("session attribute type " + value.getClass().getName()
                    + " is not allow-listed in SessionJson.ALLOWED");
        }
        String json = mapper.writerFor(type).writeValueAsString(value);
        return (type.getName() + "\n" + json).getBytes(StandardCharsets.UTF_8);
    }

    public @Nullable Object read(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        int nl = text.indexOf('\n');
        Class<?> type = nl > 0 ? ALLOWED.get(text.substring(0, nl)) : null;
        if (type == null) {
            log.warn("ignoring a session attribute of a type that is not allow-listed ({} bytes)", bytes.length);
            return null;
        }
        try {
            return mapper.readerFor(type).readValue(text.substring(nl + 1));
        } catch (RuntimeException e) {   // Jackson 3 exceptions are unchecked
            log.warn("ignoring an unreadable session attribute of type {}: {}", type.getName(), e.getMessage());
            return null;
        }
    }
}
