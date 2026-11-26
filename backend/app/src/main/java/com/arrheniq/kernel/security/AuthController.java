package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Browser entry points of the BFF login (ADR 0006). Tokens never reach the browser; these endpoints
 * only redirect. Logout is {@code POST /auth/logout} (Spring Security, CSRF-protected).
 */
@RestController
public class AuthController {

    private final ArrheniqSecurityProperties props;
    private final Clock clock;

    public AuthController(ArrheniqSecurityProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** Starts login; remembers a safe return path. */
    @PublicEndpoint
    @GetMapping("/auth/login")
    public ResponseEntity<Void> login(@RequestParam(name = "returnTo", required = false) @Nullable String returnTo,
                                      HttpServletRequest request) {
        if (SafeRedirects.isSafe(returnTo)) {
            request.getSession(true).setAttribute(SessionKeys.RETURN_TO, returnTo);
        }
        return redirect(request.getContextPath() + "/oauth2/authorization/" + props.registrationId());
    }

    /**
     * Starts re-authentication for an e-signature ({@code purpose=SIGN}) or to unlock an idle-locked
     * session ({@code purpose=UNLOCK}). Requires a signed-in user (enforced by the filter chain).
     * For SIGN the caller names what will be signed ({@code recordType}, {@code recordId}, {@code recordVersion});
     * the resulting evidence can sign only that record version ({@link SigningIntent}).
     */
    @AuthenticatedOnly
    @GetMapping("/auth/reauth")
    public ResponseEntity<Void> reauth(@RequestParam(name = "purpose", defaultValue = "SIGN") String purpose,
                                       @RequestParam(name = "returnTo", required = false) @Nullable String returnTo,
                                       @RequestParam(name = "recordType", required = false) @Nullable String recordType,
                                       @RequestParam(name = "recordId", required = false) @Nullable String recordId,
                                       @RequestParam(name = "recordVersion", required = false) @Nullable String recordVersion,
                                       HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (session == null || auth == null || !(auth.getPrincipal() instanceof ArrheniqUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ReauthRequest.Purpose p;
        SigningIntent intent = null;
        try {
            p = ReauthRequest.Purpose.valueOf(purpose.toUpperCase(Locale.ROOT));
            if (p == ReauthRequest.Purpose.SIGN) {
                if (recordType == null || recordId == null || recordVersion == null) {
                    return ResponseEntity.badRequest().build();
                }
                intent = new SigningIntent(UUID.randomUUID(), recordType, UUID.fromString(recordId),
                        Long.parseLong(recordVersion));
            }
        } catch (IllegalArgumentException e) {   // also NumberFormatException
            return ResponseEntity.badRequest().build();
        }
        session.setAttribute(SessionKeys.PENDING_REAUTH, new ReauthRequest(user.tenantId(), user.userId(), p,
                clock.instant(), SafeRedirects.orDefault(returnTo, props.postLoginPath()), intent));
        session.removeAttribute(SessionKeys.REAUTH_EVIDENCE);
        return redirect(request.getContextPath() + "/oauth2/authorization/" + props.registrationId()
                + "?" + ReauthAuthorizationRequestCustomizer.REAUTH_PARAM + "=1");
    }

    /** Landing page after logout or a failed login; the SPA shows the message. */
    @PublicEndpoint
    @GetMapping("/auth/signed-out")
    public Map<String, String> signedOut(@RequestParam(name = "error", required = false) @Nullable String error) {
        String status = "login".equals(error) || "reauth".equals(error) ? error + "_failed" : "signed_out";
        return Map.of("status", status);
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }
}
