package fixtures.endpointrules;

import com.arrheniq.kernel.security.AuthenticatedOnly;
import com.arrheniq.kernel.security.PublicEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GuardedController {

    private final GuardedService service;

    public GuardedController(GuardedService service) {
        this.service = service;
    }

    @GetMapping("/things")
    public String viaService() {
        return service.read();
    }

    @AuthenticatedOnly
    @GetMapping("/me")
    public String me() {
        return "me";
    }

    @PublicEndpoint
    @GetMapping("/hello")
    public String hello() {
        return "hello";
    }
}
