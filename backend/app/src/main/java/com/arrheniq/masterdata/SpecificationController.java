package com.arrheniq.masterdata;

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
import java.util.Objects;
import java.util.UUID;

/**
 * REST API of specifications and their versions. Permissions and site scope are enforced by
 * {@link SpecificationService}. Approving is an e-signature for one version: the SPA first sends the user through
 * {@code /auth/reauth?purpose=SIGN&recordType=SPEC_VERSION&recordId=<version id>&recordVersion=…}.
 */
@RestController
public class SpecificationController {

    private final SpecificationService service;
    private final ReauthGuard reauth;

    public SpecificationController(SpecificationService service, ReauthGuard reauth) {
        this.service = service;
        this.reauth = reauth;
    }

    @GetMapping("/api/v1/specifications")
    public List<SpecificationSummary> list(@RequestParam(name = "productId", required = false) @Nullable UUID productId,
                                           @RequestParam(name = "q", required = false) @Nullable String q,
                                           @RequestParam(name = "limit", defaultValue = "50") int limit,
                                           @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return service.list(productId, q, limit, offset);
    }

    @PostMapping("/api/v1/specifications")
    public ResponseEntity<SpecificationDetails> create(@RequestBody Requests.SpecificationCreate body) {
        SpecificationDetails created = service.create(body.productId(), body.specNumber(), body.title());
        return ResponseEntity.created(URI.create("/api/v1/specifications/" + created.specification().id()))
                .body(created);
    }

    @GetMapping("/api/v1/specifications/{id}")
    public SpecificationDetails get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping("/api/v1/specifications/{id}/versions")
    public ResponseEntity<SpecVersionDetails> newVersion(@PathVariable("id") UUID id) {
        SpecVersionDetails created = service.newVersion(id);
        return ResponseEntity.created(URI.create("/api/v1/spec-versions/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/spec-versions/{id}")
    public SpecVersionDetails version(@PathVariable("id") UUID id) {
        return service.version(id);
    }

    @PutMapping("/api/v1/spec-versions/{id}")
    public SpecVersionDetails save(@PathVariable("id") UUID id, @RequestBody Requests.SpecVersionUpdate body) {
        Requests.SpecContent data = Objects.requireNonNullElse(body.data(), new Requests.SpecContent(null, null));
        return service.saveDraft(id, Requests.version(body.expectedVersion()), data.changeSummary(),
                Objects.requireNonNullElse(data.tests(), List.of()));
    }

    @PostMapping("/api/v1/spec-versions/{id}/submit")
    public SpecVersionDetails submit(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        return service.submit(id, Requests.version(body.expectedVersion()));
    }

    @PostMapping("/api/v1/spec-versions/{id}/approve")
    public SpecVersionDetails approve(@PathVariable("id") UUID id, @RequestBody Requests.Approve body,
                                      HttpSession session) {
        return service.approve(id, Requests.version(body.expectedVersion()), body.comment(),
                () -> reauth.consumeForSignature(session));
    }

    @PostMapping("/api/v1/spec-versions/{id}/reject")
    public SpecVersionDetails reject(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        return service.reject(id, Requests.version(body.expectedVersion()), body.reason());
    }

    @PostMapping("/api/v1/spec-versions/{id}/retire")
    public SpecVersionDetails retire(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        return service.retire(id, Requests.version(body.expectedVersion()), body.reason());
    }

    /** Discards a draft (logically deleted, with a reason). */
    @PostMapping("/api/v1/spec-versions/{id}/discard")
    public ResponseEntity<Void> discard(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        service.discard(id, Requests.version(body.expectedVersion()), body.reason());
        return ResponseEntity.noContent().build();
    }
}
