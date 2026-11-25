package com.arrheniq.masterdata;

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

/** REST API of the test library. Permissions are enforced by {@link TestMethodService}. */
@RestController
@RequestMapping("/api/v1/test-methods")
public class TestMethodController {

    private final TestMethodService service;

    public TestMethodController(TestMethodService service) {
        this.service = service;
    }

    @GetMapping
    public List<TestMethod> list(@RequestParam(name = "status", required = false) @Nullable String status,
                                 @RequestParam(name = "q", required = false) @Nullable String q,
                                 @RequestParam(name = "limit", defaultValue = "50") int limit,
                                 @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return service.list(status, q, limit, offset);
    }

    @GetMapping("/{id}")
    public TestMethod get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<TestMethod> create(@RequestBody TestMethodInput input) {
        TestMethod created = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/test-methods/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public TestMethod update(@PathVariable("id") UUID id, @RequestBody Requests.TestMethodUpdate body) {
        long version = Requests.version(body.expectedVersion());
        return service.update(id, version, Objects.requireNonNullElse(body.data(), empty()), body.reason());
    }

    @PostMapping("/{id}/retire")
    public TestMethod retire(@PathVariable("id") UUID id, @RequestBody Requests.StatusChange body) {
        return service.retire(id, Requests.version(body.expectedVersion()), body.reason());
    }

    private static TestMethodInput empty() {
        return new TestMethodInput(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
