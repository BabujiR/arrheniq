package fixtures.endpointrules;

import com.arrheniq.kernel.security.RequirePermission;
import org.springframework.stereotype.Service;

@Service
public class GuardedService {

    @RequirePermission("thing.view")
    public String read() {
        return "ok";
    }
}
