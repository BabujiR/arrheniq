package com.arrheniq.kernel.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

/** Signing was attempted without fresh re-authentication (mapped to HTTP 401 with code ARQ-AUTH-REAUTH). */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class ReauthRequiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReauthRequiredException() {
        super("ARQ-AUTH-REAUTH: re-authentication required");
    }
}
