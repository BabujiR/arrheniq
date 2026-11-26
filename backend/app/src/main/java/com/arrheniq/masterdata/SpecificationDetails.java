package com.arrheniq.masterdata;

import java.util.List;

/** A specification with its full version history, newest first. */
public record SpecificationDetails(SpecificationSummary specification, List<SpecVersionSummary> versions) {
}
