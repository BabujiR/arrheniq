package com.arrheniq.kernel.security;

import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.FieldChange;
import com.arrheniq.kernel.tenancy.TenantContext;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Runs after Spring Security has authenticated the request. For a signed-in user it:
 * <ol>
 *   <li>rejects a session used on another tenant's host (ARQ-AUTH-TENANT)</li>
 *   <li>applies {@link SessionPolicy}: absolute expiry ends the session (ARQ-AUTH-EXPIRED); idle time
 *       locks it until re-authentication (ARQ-AUTH-LOCKED)</li>
 *   <li>loads status and permissions fresh from the database; a deactivated user or suspended tenant
 *       ends the session (ARQ-AUTH-REVOKED)</li>
 *   <li>binds {@link CurrentUser} and the {@link TenantContext} for the rest of the request</li>
 * </ol>
 * Not a Spring bean on purpose: it is added to the security filter chain only, never as a servlet filter.
 */
public class SessionContextFilter extends OncePerRequestFilter {

    /** Session activity is written at most this often (avoids a session write per request). */
    static final Duration ACTIVITY_GRANULARITY = Duration.ofSeconds(30);

    private final CurrentUserLoader loader;
    private final TenantResolver tenants;
    private final SecurityAuditor auditor;
    private final ArrheniqSecurityProperties props;
    private final Clock clock;

    public SessionContextFilter(CurrentUserLoader loader, TenantResolver tenants, SecurityAuditor auditor,
                                ArrheniqSecurityProperties props, Clock clock) {
        this.loader = loader;
        this.tenants = tenants;
        this.auditor = auditor;
        this.props = props;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (auth == null || !(auth.getPrincipal() instanceof ArrheniqUser user) || session == null) {
            chain.doFilter(request, response);
            return;
        }
        boolean authPath = isAuthPath(request);

        Optional<ResolvedTenant> hostTenant = tenants.resolve(request);
        if (hostTenant.isPresent() && !hostTenant.get().id().equals(user.tenantId())) {
            endSession(session);
            ProblemResponses.write(response, 401, "Session belongs to another tenant", "ARQ-AUTH-TENANT");
            return;
        }

        Instant now = clock.instant();
        Instant loginAt = session.getAttribute(SessionKeys.LOGIN_AT) instanceof Instant i ? i : null;
        Instant lastActivity = session.getAttribute(SessionKeys.LAST_ACTIVITY) instanceof Instant i ? i : null;
        Duration idleLock = session.getAttribute(SessionKeys.IDLE_LOCK) instanceof Duration d ? d : props.defaultIdleLock();
        boolean locked = Boolean.TRUE.equals(session.getAttribute(SessionKeys.LOCKED));

        switch (SessionPolicy.evaluate(now, loginAt, lastActivity, locked, idleLock, props.absoluteSessionLifetime())) {
            case EXPIRED -> {
                auditor.record(user.tenantId(), SecurityAuditor.actorFor(user), "SESSION_EXPIRED", user.userId(),
                        user.username(), List.of(new FieldChange("loginAt", null, loginAt)));
                endSession(session);
                ProblemResponses.write(response, 401, "Session expired", "ARQ-AUTH-EXPIRED");
                return;
            }
            case LOCKED -> {
                if (!locked) {
                    session.setAttribute(SessionKeys.LOCKED, Boolean.TRUE);
                    auditor.record(user.tenantId(), SecurityAuditor.actorFor(user), "SESSION_LOCKED", user.userId(),
                            user.username(), List.of(new FieldChange("lastActivity", null, lastActivity)));
                }
                if (!authPath) {
                    ProblemResponses.write(response, 401, "Session locked", "ARQ-AUTH-LOCKED");
                    return;
                }
            }
            case ACTIVE -> {
                if (!authPath && SessionPolicy.shouldRecordActivity(now, lastActivity, ACTIVITY_GRANULARITY)) {
                    session.setAttribute(SessionKeys.LAST_ACTIVITY, now);
                }
            }
        }

        Optional<CurrentUser> current = loader.load(user);
        if (current.isEmpty()) {
            auditor.record(user.tenantId(), AuditActor.system("session-check"), "SESSION_REVOKED", user.userId(),
                    user.username(), List.of(new FieldChange("reason", null, "USER_OR_TENANT_NOT_ACTIVE")));
            endSession(session);
            ProblemResponses.write(response, 401, "Access revoked", "ARQ-AUTH-REVOKED");
            return;
        }

        CurrentUser cu = current.get();
        SiteScope visible = cu.grants().visibleSites();
        try {
            CurrentUserHolder.set(cu);
            if (visible.isEmpty()) {
                // no role assignments: authenticated, but no tenant data is reachable (queries fail closed)
                chain.doFilter(request, response);
            } else {
                TenantContext ctx = new TenantContext(cu.tenantId(), Optional.of(cu.userId()), visible.allSites(),
                        visible.siteIds());
                TenantContextHolder.callAs(ctx, () -> {
                    try {
                        chain.doFilter(request, response);
                    } catch (IOException | ServletException e) {
                        throw new FilterChainException(e);
                    }
                    return null;
                });
            }
        } catch (FilterChainException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw (ServletException) e.getCause();
        } finally {
            CurrentUserHolder.clear();
        }
    }

    static boolean isAuthPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/auth/") || path.startsWith("/oauth2/") || path.startsWith("/login/");
    }

    private static void endSession(HttpSession session) {
        SecurityContextHolder.clearContext();
        try {
            session.invalidate();
        } catch (IllegalStateException alreadyInvalid) {
            // the session was invalidated concurrently; nothing left to end
            SecurityContextHolder.clearContext();
        }
    }

    /** Carries a checked filter-chain exception through the {@code Supplier} used for context binding. */
    private static final class FilterChainException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        FilterChainException(Exception cause) {
            super(cause);
        }
    }
}
