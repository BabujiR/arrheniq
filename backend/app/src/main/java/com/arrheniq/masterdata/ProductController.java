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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST API of products. Permissions and site scope are enforced by {@link ProductService}.
 * Approving is an e-signature: the SPA first sends the user through
 * {@code /auth/reauth?purpose=SIGN&returnTo=…}, then calls {@code POST /{id}/approve} within 5 minutes.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService service;
    private final ReauthGuard reauth;

    public ProductController(ProductService service, ReauthGuard reauth) {
        this.service = service;
        this.reauth = reauth;
    }

    @GetMapping
    public List<Product> list(@RequestParam(name = "status", required = false) @Nullable String status,
                              @RequestParam(name = "q", required = false) @Nullable String q,
                              @RequestParam(name = "siteId", required = false) @Nullable UUID siteId,
                              @RequestParam(name = "limit", defaultValue = "50") int limit,
                              @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return service.list(status, q, siteId, limit, offset);
    }

    @GetMapping("/{id}")
    public ProductDetails get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ProductDetails> create(@RequestBody ProductInput input) {
        ProductDetails created = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/products/" + created.product().id())).body(created);
    }

    @PutMapping("/{id}")
    public ProductDetails update(@PathVariable("id") UUID id, @RequestBody Requests.ProductUpdate body) {
        long version = Requests.version(body.expectedVersion());
        return service.update(id, version, Objects.requireNonNullElse(body.data(), empty()), body.reason());
    }

    @PostMapping("/{id}/submit")
    public ProductDetails submit(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        return service.submit(id, Requests.version(body.expectedVersion()));
    }

    @PostMapping("/{id}/approve")
    public ProductDetails approve(@PathVariable("id") UUID id, @RequestBody Requests.Approve body,
                                  HttpSession session) {
        long version = Requests.version(body.expectedVersion());
        return service.approve(id, version, body.comment(), () -> reauth.consumeForSignature(session));
    }

    @PostMapping("/{id}/reject")
    public ProductDetails reject(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        return service.reject(id, Requests.version(body.expectedVersion()), body.reason());
    }

    @PostMapping("/{id}/retire")
    public ProductDetails retire(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        return service.retire(id, Requests.version(body.expectedVersion()), body.reason());
    }

    private static ProductInput empty() {
        return new ProductInput(null, null, null, null, null, null, null, null, null, null, null);
    }
}
