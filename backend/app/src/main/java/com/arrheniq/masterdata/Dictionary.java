package com.arrheniq.masterdata;

import java.util.UUID;

/**
 * A controlled list (URS-MD-006), e.g. storage conditions or units. Every tenant starts with the catalog lists and
 * values (V10 seed, copied at provisioning).
 *
 * @param systemManaged values come from the catalog only; users can't add or change them
 */
public record Dictionary(UUID id, String code, String name, String category, boolean systemManaged,
                         int activeValues, int retiredValues) {
}
