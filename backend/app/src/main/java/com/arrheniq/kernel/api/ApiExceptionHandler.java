package com.arrheniq.kernel.api;

import com.arrheniq.kernel.audit.ReasonRequiredException;
import com.arrheniq.kernel.security.ReauthRequiredException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps exceptions to RFC 9457 problem responses with a stable {@code code} (and {@code errors} for
 * field validation). Unknown exceptions are left to Spring's default handling (500, no details).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) {
        ProblemDetail p = problem(HttpStatus.valueOf(e.status()), e.getMessage(), e.code());
        if (!e.errors().isEmpty()) {
            p.setProperty("errors", e.errors());
        }
        return p;
    }

    @ExceptionHandler(ReasonRequiredException.class)
    ProblemDetail reasonRequired(ReasonRequiredException e) {
        return problem(HttpStatus.valueOf(422), "A reason for the change is required", "ARQ-AUDIT-010");
    }

    @ExceptionHandler(ReauthRequiredException.class)
    ProblemDetail reauthRequired(ReauthRequiredException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Re-authentication required to sign", "ARQ-AUTH-REAUTH");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Missing permission", "ARQ-AUTHZ-001");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicate(DuplicateKeyException e) {
        return problem(HttpStatus.CONFLICT, "Already exists", "ARQ-VAL-002");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request body", "ARQ-VAL-000");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String code) {
        ProblemDetail p = ProblemDetail.forStatus(status);
        p.setTitle(title);
        p.setProperty("code", code);
        return p;
    }
}
