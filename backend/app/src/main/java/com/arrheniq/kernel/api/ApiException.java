package com.arrheniq.kernel.api;

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.List;

/**
 * A domain or request error with a stable code, mapped to an RFC 9457 problem response by
 * {@link ApiExceptionHandler}. Messages never contain stack traces or other tenants' data.
 */
public class ApiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final transient List<FieldError> errors;

    public ApiException(int status, String code, String title, List<FieldError> errors) {
        super(title);
        this.status = status;
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public static ApiException notFound(String what) {
        return new ApiException(404, "ARQ-NF-001", what + " not found", List.of());
    }

    public static ApiException invalid(List<FieldError> errors) {
        return new ApiException(400, "ARQ-VAL-001", "Validation failed", errors);
    }

    public static ApiException duplicate(String field, String value) {
        return new ApiException(409, "ARQ-VAL-002", "Already exists",
                List.of(new FieldError(field, "DUPLICATE", "'" + value + "' is already used")));
    }

    public static ApiException versionConflict() {
        return new ApiException(409, "ARQ-CONC-001",
                "The record was changed by someone else; reload and try again", List.of());
    }

    public static ApiException invalidState(String status, String action) {
        return new ApiException(409, "ARQ-STATE-001", "Action " + action + " is not allowed in status " + status,
                List.of());
    }

    public static ApiException segregationOfDuties() {
        return new ApiException(403, "ARQ-SOD-001",
                "The person who last changed or submitted the record can't approve it", List.of());
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<FieldError> errors() {
        return errors;
    }

    /** One problem with one input field. */
    public record FieldError(String field, String code, @Nullable String message) { }
}
