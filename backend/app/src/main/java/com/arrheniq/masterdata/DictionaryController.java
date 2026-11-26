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

/** REST API of the controlled lists. Permissions and site scope are enforced by {@link DictionaryService}. */
@RestController
@RequestMapping("/api/v1/dictionaries")
public class DictionaryController {

    private final DictionaryService service;

    public DictionaryController(DictionaryService service) {
        this.service = service;
    }

    @GetMapping
    public List<Dictionary> lists() {
        return service.lists();
    }

    @GetMapping("/{code}/values")
    public List<DictionaryValue> values(@PathVariable("code") String code,
                                        @RequestParam(name = "status", required = false) @Nullable String status) {
        return service.values(code, status);
    }

    @PostMapping("/{code}/values")
    public ResponseEntity<DictionaryValue> create(@PathVariable("code") String code,
                                                  @RequestBody DictionaryValueInput input) {
        DictionaryValue created = service.create(code, input);
        return ResponseEntity.created(URI.create("/api/v1/dictionaries/" + created.dictionaryCode() + "/values/"
                + created.id())).body(created);
    }

    @PutMapping("/{code}/values/{id}")
    public DictionaryValue update(@PathVariable("code") String code, @PathVariable("id") UUID id,
                                  @RequestBody Requests.DictionaryValueUpdate body) {
        return service.update(code, id, Requests.version(body.expectedVersion()),
                Objects.requireNonNullElse(body.data(), new DictionaryValueInput(null, null, null, null, null, null)),
                body.reason());
    }

    @PostMapping("/{code}/values/{id}/retire")
    public DictionaryValue retire(@PathVariable("code") String code, @PathVariable("id") UUID id,
                                  @RequestBody Requests.StatusChange body) {
        return service.retire(code, id, Requests.version(body.expectedVersion()), body.reason());
    }

    @PostMapping("/{code}/values/{id}/reactivate")
    public DictionaryValue reactivate(@PathVariable("code") String code, @PathVariable("id") UUID id,
                                      @RequestBody Requests.StatusChange body) {
        return service.reactivate(code, id, Requests.version(body.expectedVersion()), body.reason());
    }

    /** Logical delete with a reason; only for values that are neither from the catalog nor in use. */
    @PostMapping("/{code}/values/{id}/delete")
    public ResponseEntity<Void> delete(@PathVariable("code") String code, @PathVariable("id") UUID id,
                                       @RequestBody Requests.StatusChange body) {
        service.delete(code, id, Requests.version(body.expectedVersion()), body.reason());
        return ResponseEntity.noContent().build();
    }
}
