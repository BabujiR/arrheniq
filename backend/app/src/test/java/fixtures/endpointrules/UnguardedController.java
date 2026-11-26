package fixtures.endpointrules;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnguardedController {

    private final UnguardedService service;
    private final SystemTaskService system;

    public UnguardedController(UnguardedService service, SystemTaskService system) {
        this.service = service;
        this.system = system;
    }

    /** Violates the endpoint rule: calls only an unguarded method. */
    @GetMapping("/leak")
    public String leak() {
        return service.read();
    }

    /** Violates the "no system operations" rule (and the endpoint rule). */
    @PostMapping("/purge")
    public void purge() {
        system.purge();
    }
}
