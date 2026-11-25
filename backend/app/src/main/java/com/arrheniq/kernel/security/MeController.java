package com.arrheniq.kernel.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Who am I" for the SPA: identity, tenant, effective permissions and site scope (drives which UI
 * controls are shown; the server still enforces every permission). Also issues the CSRF cookie.
 */
@RestController
public class MeController {

    /** Response body of {@code GET /api/v1/me}. */
    public record Me(UUID userId, String username, String displayName, String tenant, Set<String> permissions,
                     boolean allSites, List<UUID> siteIds, long idleLockSeconds) { }

    private final ArrheniqSecurityProperties props;

    public MeController(ArrheniqSecurityProperties props) {
        this.props = props;
    }

    @AuthenticatedOnly
    @GetMapping("/api/v1/me")
    public Me me(HttpSession session, CsrfToken csrf) {
        csrf.getToken();   // materialises the deferred token so the XSRF-TOKEN cookie is sent
        CurrentUser u = CurrentUserHolder.require();
        SiteScope sites = u.grants().visibleSites();
        Duration idle = session.getAttribute(SessionKeys.IDLE_LOCK) instanceof Duration d ? d : props.defaultIdleLock();
        return new Me(u.userId(), u.username(), u.displayName(), u.tenantSlug(), u.grants().permissions(),
                sites.allSites(), sites.siteIds().stream().sorted().toList(), idle.toSeconds());
    }
}
