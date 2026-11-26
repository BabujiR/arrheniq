package com.arrheniq.masterdata;

import java.util.List;

/** A protocol with its full version history, newest first. */
public record ProtocolDetails(ProtocolSummary protocol, List<ProtocolVersionSummary> versions) {
}
