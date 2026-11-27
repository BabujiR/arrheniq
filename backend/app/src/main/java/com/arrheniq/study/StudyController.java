package com.arrheniq.study;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.security.ReauthGuard;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST API of studies. Permissions and site scope are enforced by {@link StudyService}. Approving is an
 * e-signature for one study ({@code recordType=STUDY} in the signing intent).
 */
@RestController
public class StudyController {

    /** Submit, reject or discard: the version you loaded (optimistic locking) and, where needed, a reason. */
    public record StatusChange(@Nullable Long expectedVersion, @Nullable String reason) { }

    /** Approve with e-signature; needs fresh re-authentication (purpose SIGN). */
    public record Approve(@Nullable Long expectedVersion, @Nullable String comment) { }

    private final StudyService service;
    private final ReauthGuard reauth;

    public StudyController(StudyService service, ReauthGuard reauth) {
        this.service = service;
        this.reauth = reauth;
    }

    @GetMapping("/api/v1/studies")
    public List<StudySummary> list(@RequestParam(name = "productId", required = false) @Nullable UUID productId,
                                   @RequestParam(name = "status", required = false) @Nullable String status,
                                   @RequestParam(name = "q", required = false) @Nullable String q,
                                   @RequestParam(name = "limit", defaultValue = "50") int limit,
                                   @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return service.list(productId, status, q, limit, offset);
    }

    /** Logs in a lot under one or more conditions; answers the created studies (one per condition). */
    @PostMapping("/api/v1/studies")
    public ResponseEntity<List<StudySummary>> login(@RequestBody StudyLoginInput body) {
        List<StudySummary> created = service.login(body);
        return ResponseEntity.created(URI.create("/api/v1/studies/" + created.getFirst().id())).body(created);
    }

    @GetMapping("/api/v1/studies/{id}")
    public StudyDetails get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PutMapping("/api/v1/studies/{id}")
    public StudyDetails update(@PathVariable("id") UUID id, @RequestBody StudyUpdateInput body) {
        return service.update(id, version(body.expectedVersion()), body);
    }

    @PostMapping("/api/v1/studies/{id}/submit")
    public StudyDetails submit(@PathVariable("id") UUID id, @RequestBody StatusChange body) {
        return service.submit(id, version(body.expectedVersion()));
    }

    @PostMapping("/api/v1/studies/{id}/approve")
    public StudyDetails approve(@PathVariable("id") UUID id, @RequestBody Approve body, HttpSession session) {
        return service.approve(id, version(body.expectedVersion()), body.comment(),
                () -> reauth.consumeForSignature(session));
    }

    @PostMapping("/api/v1/studies/{id}/reject")
    public StudyDetails reject(@PathVariable("id") UUID id, @RequestBody StatusChange body) {
        return service.reject(id, version(body.expectedVersion()), body.reason());
    }

    @PostMapping("/api/v1/studies/{id}/discard")
    public ResponseEntity<Void> discard(@PathVariable("id") UUID id, @RequestBody StatusChange body) {
        service.discard(id, version(body.expectedVersion()), body.reason());
        return ResponseEntity.noContent().build();
    }

    private static long version(@Nullable Long v) {
        if (v == null || v < 0) {
            throw ApiException.invalid(List.of(new ApiException.FieldError("expectedVersion", "REQUIRED",
                    "the version you loaded")));
        }
        return v;
    }
}
