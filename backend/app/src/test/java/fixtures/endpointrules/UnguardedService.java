package fixtures.endpointrules;

import org.springframework.stereotype.Service;

@Service
public class UnguardedService {

    /** Violates the service rule: no access declaration. */
    public String read() {
        return "leak";
    }
}
