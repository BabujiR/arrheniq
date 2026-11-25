package com.arrheniq.kernel.audit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrictJsonReaderTest {

    @Test
    void readsPostgresJsonbOutputFormat() {
        // jsonb::text reorders keys and adds spaces after ':' and ','
        Object v = StrictJsonReader.read("[{\"new\": 1.50, \"old\": null, \"field\": \"assay\"}]");
        Map<?, ?> m = (Map<?, ?>) ((List<?>) v).get(0);
        assertEquals("assay", m.get("field"));
        assertEquals(new BigDecimal("1.50"), m.get("new"));
        assertEquals("[{\"field\":\"assay\",\"new\":1.5,\"old\":null}]", CanonicalJson.write(v));
    }

    @Test
    void unicodeAndEscapesRoundTrip() {
        String original = "line1\nline2 \"quoted\" é \u0007";
        assertEquals(original, StrictJsonReader.read(CanonicalJson.write(original)));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> StrictJsonReader.read("{\"a\":1,}"));
        assertThrows(IllegalArgumentException.class, () -> StrictJsonReader.read("{\"a\":1,\"a\":2}"));
        assertThrows(IllegalArgumentException.class, () -> StrictJsonReader.read("[1] x"));
    }
}
