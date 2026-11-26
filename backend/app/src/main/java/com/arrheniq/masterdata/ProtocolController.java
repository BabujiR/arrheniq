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
 * REST API of protocols and their versions. Permissions and site scope are enforced by {@link ProtocolService}.
 * Approving is an e-signature for one version ({@code recordType=PROTOCOL_VERSION} in the signing intent).
 */
@RestController
public class ProtocolController {

    private final ProtocolService service;
    private final ReauthGuard reauth;

    public ProtocolController(ProtocolService service, ReauthGuard reauth) {
        this.service = service;
        this.reauth = reauth;
    }

    @GetMapping("/api/v1/protocols")
    public List<ProtocolSummary> list(@RequestParam(name = "productId", required = false) @Nullable UUID productId,
                                      @RequestParam(name = "q", required = false) @Nullable String q,
                                      @RequestParam(name = "limit", defaultValue = "50") int limit,
                                      @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return service.list(productId, q, limit, offset);
    }

    @PostMapping("/api/v1/protocols")
    public ResponseEntity<ProtocolDetails> create(@RequestBody Requests.ProtocolCreate body) {
        ProtocolDetails created = service.create(body.productId(), body.protocolNumber(), body.name(),
                body.templateCode(), body.specVersionId());
        return ResponseEntity.created(URI.create("/api/v1/protocols/" + created.protocol().id())).body(created);
    }

    @GetMapping("/api/v1/protocols/{id}")
    public ProtocolDetails get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping("/api/v1/protocols/{id}/versions")
    public ResponseEntity<ProtocolVersionDetails> newVersion(@PathVariable("id") UUID id) {
        ProtocolVersionDetails created = service.newVersion(id);
        return ResponseEntity.created(URI.create("/api/v1/protocol-versions/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/protocol-versions/{id}")
    public ProtocolVersionDetails version(@PathVariable("id") UUID id) {
        return service.version(id);
    }

    @PutMapping("/api/v1/protocol-versions/{id}")
    public ProtocolVersionDetails save(@PathVariable("id") UUID id, @RequestBody Requests.ProtocolVersionUpdate body) {
        ProtocolInput data = Objects.requireNonNullElse(body.data(),
                new ProtocolInput(null, null, null, null, null, null, null));
        return service.saveDraft(id, Requests.version(body.expectedVersion()), data);
    }

    @PostMapping("/api/v1/protocol-versions/{id}/submit")
    public ProtocolVersionDetails submit(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        return service.submit(id, Requests.version(body.expectedVersion()));
    }

    @PostMapping("/api/v1/protocol-versions/{id}/approve")
    public ProtocolVersionDetails approve(@PathVariable("id") UUID id, @RequestBody Requests.Approve body,
                                          HttpSession session) {
        return service.approve(id, Requests.version(body.expectedVersion()), body.comment(),
                () -> reauth.consumeForSignature(session));
    }

    @PostMapping("/api/v1/protocol-versions/{id}/reject")
    public ProtocolVersionDetails reject(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        return service.reject(id, Requests.version(body.expectedVersion()), body.reason());
    }

    @PostMapping("/api/v1/protocol-versions/{id}/retire")
    public ProtocolVersionDetails retire(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        return service.retire(id, Requests.version(body.expectedVersion()), body.reason());
    }

    @PostMapping("/api/v1/protocol-versions/{id}/discard")
    public ResponseEntity<Void> discard(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        service.discard(id, Requests.version(body.expectedVersion()), body.reason());
        return ResponseEntity.noContent().build();
    }
}
