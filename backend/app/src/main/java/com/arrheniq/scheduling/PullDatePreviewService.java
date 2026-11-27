package com.arrheniq.scheduling;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.security.RequirePermission;
import com.arrheniq.masterdata.ProtocolContent;
import com.arrheniq.masterdata.ProtocolService;
import com.arrheniq.masterdata.ProtocolVersionDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pull-date preview for a protocol version at its site (URS-SCH-001/002). Read-only. */
@Service
public class PullDatePreviewService {

    /** Previews are for planning: on-stability dates outside these years are refused. */
    private static final int MIN_YEAR = 1990;
    private static final int MAX_YEAR = 2100;

    private final ProtocolService protocols;
    private final SchedulingSettings settings;

    PullDatePreviewService(ProtocolService protocols, SchedulingSettings settings) {
        this.protocols = protocols;
        this.settings = settings;
    }

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public PullDatePreview preview(UUID protocolVersionId, LocalDate onStabilityDate) {
        if (onStabilityDate.getYear() < MIN_YEAR || onStabilityDate.getYear() > MAX_YEAR) {
            throw ApiException.invalid(List.of(new ApiException.FieldError("onStabilityDate", "OUT_OF_RANGE",
                    MIN_YEAR + "–" + MAX_YEAR)));
        }
        ProtocolVersionDetails v = protocols.version(protocolVersionId);   // checks the site
        UUID siteId = v.protocol().siteId();
        SchedulingRules rules = settings.forSite(siteId);
        List<PullDatePreview.Line> lines = new ArrayList<>();
        for (ProtocolContent.TimePoint t : v.content().timepoints()) {
            long value = wholeNumber(t.value());
            PullDate d;
            try {
                d = PullDateCalculator.calculate(onStabilityDate, value, t.unit(), rules);
            } catch (IllegalArgumentException e) {   // e.g. a holiday calendar with no working day for a month
                throw ApiException.invalid(List.of(new ApiException.FieldError("timepoints", "NOT_SCHEDULABLE",
                        t.label() + ": " + e.getMessage())));
            }
            lines.add(new PullDatePreview.Line(t.label(), value, d.unit(), d.calculatedDate(), d.scheduledDate(),
                    d.ruleApplied()));
        }
        return new PullDatePreview(PullDateCalculator.ALGORITHM, protocolVersionId, siteId, onStabilityDate,
                new PullDatePreview.Rules(rules.saturday().name(), rules.sunday().name(), rules.holiday().name(),
                        List.copyOf(rules.holidays())),
                lines);
    }

    /** Protocol time points are whole numbers (ProtocolRules); a fractional one is an error, never rounded. */
    private static long wholeNumber(BigDecimal value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            throw ApiException.invalid(List.of(new ApiException.FieldError("timepoints", "FORMAT",
                    "time point " + value.toPlainString() + " is not a whole number; correct the protocol")));
        }
    }
}
