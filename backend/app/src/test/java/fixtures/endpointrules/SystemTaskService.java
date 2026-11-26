package fixtures.endpointrules;

import com.arrheniq.kernel.security.SystemOperation;
import org.springframework.stereotype.Service;

@Service
public class SystemTaskService {

    @SystemOperation
    public void purge() {
    }
}
