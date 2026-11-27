package com.arrheniq.scheduling;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.AuditEventDraft;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.audit.FieldChange;
import com.arrheniq.kernel.security.CurrentUser;
import com.arrheniq.kernel.security.CurrentUserHolder;
import com.arrheniq.study.StudyApproved;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Generates a study's schedule when it is approved (URS-STU-005, URS-SCH-001/002): one time point per protocol time
 * point, with its calendar date, pull date, the rule that moved it and the pull window. Runs synchronously in the
 * approving transaction; if anything fails, the approval and the signature roll back with it. The database refuses
 * to commit an approved study without time points (V15).
 */
@Component
class ScheduleGenerator {

    private record ProtocolTimepoint(UUID id, BigDecimal value, String unit, String label, int unitsPlanned) { }

    private record Planned(ProtocolTimepoint source, long value, PullDate date) { }

    private final JdbcClient jdbc;
    private final SchedulingSettings settings;
    private final AuditService audit;

    ScheduleGenerator(JdbcClient jdbc, SchedulingSettings settings, AuditService audit) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.audit = audit;
    }

    @EventListener
    void on(StudyApproved study) {
        SchedulingRules rules = settings.forSite(study.siteId());
        int window = settings.windowDays(study.siteId());
        List<ProtocolTimepoint> source = jdbc.sql("""
                SELECT id, value, unit, COALESCE(label, '') AS label, units_planned FROM app.protocol_timepoint
                 WHERE protocol_version_id = ? AND deleted_at IS NULL
                """)
            .param(study.protocolVersionId())
            .query((rs, _) -> new ProtocolTimepoint(rs.getObject("id", UUID.class), rs.getBigDecimal("value"),
                    rs.getString("unit"), rs.getString("label"), rs.getInt("units_planned")))
            .list();
        if (source.isEmpty()) {
            throw new IllegalStateException("protocol version " + study.protocolVersionId() + " has no time points");
        }
        List<Planned> planned = new ArrayList<>();
        for (ProtocolTimepoint t : source) {
            long value;
            try {
                value = t.value().longValueExact();
            } catch (ArithmeticException e) {
                throw ApiException.invalid(List.of(new ApiException.FieldError("protocolVersionId", "FORMAT",
                        "time point " + t.value().toPlainString() + " " + t.unit() + " is not a whole number")));
            }
            try {
                planned.add(new Planned(t, value,
                        PullDateCalculator.calculate(study.onStabilityDate(), value, t.unit(), rules)));
            } catch (IllegalArgumentException e) {
                throw ApiException.invalid(List.of(new ApiException.FieldError("onStabilityDate", "NOT_SCHEDULABLE",
                        label(t, value) + ": " + e.getMessage())));
            }
        }
        planned.sort(Comparator.comparing((Planned p) -> p.date().calculatedDate()).thenComparingLong(Planned::value));
        refuseBackEntry(study, planned);

        CurrentUser me = CurrentUserHolder.require();
        List<FieldChange> changes = new ArrayList<>();
        changes.add(new FieldChange("scheduleAlgorithm", null, PullDateCalculator.ALGORITHM));
        for (int seq = 0; seq < planned.size(); seq++) {
            Planned p = planned.get(seq);
            String label = label(p.source(), p.value());
            jdbc.sql("""
                    INSERT INTO app.study_timepoint (tenant_id, id, created_by, study_id, protocol_timepoint_id, seq,
                                                     value, unit, label, calculated_date, scheduled_date,
                                                     date_rule_applied, window_days_before, window_days_after,
                                                     units_planned)
                    VALUES (app.current_tenant(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                // as a list: date_rule_applied may be NULL (JdbcClient's varargs are declared non-null)
                .params(Arrays.asList(UUID.randomUUID(), me.userId(), study.studyId(), p.source().id(), seq,
                        p.value(), p.date().unit(), label, p.date().calculatedDate(), p.date().scheduledDate(),
                        p.date().ruleApplied(), window, window, p.source().unitsPlanned()))
                .update();
            changes.add(new FieldChange("tp." + label + ".pullDate", null, p.date().scheduledDate().toString()));
        }
        jdbc.sql("UPDATE app.study SET schedule_algorithm = ? WHERE id = ?")
                .params(PullDateCalculator.ALGORITHM, study.studyId())
                .update();
        audit.record(new AuditActor(me.userId(), me.displayName(), AuditActor.Type.USER),
                new AuditEventDraft("SCHEDULE_GENERATE", "STUDY", study.studyId(), null, study.studyCode(),
                        study.siteId(), changes, null, null, null));
    }

    /**
     * Pull dates already in the past (other than the initial time point) mean back-entry, which needs an explicit
     * attestation (URS-STU-007, S2.4). Until then such a study can't be approved.
     */
    private void refuseBackEntry(StudyApproved study, List<Planned> planned) {
        String zone = jdbc.sql("SELECT time_zone FROM app.site WHERE id = ?")
                .param(study.siteId()).query(String.class).single();
        LocalDate today = LocalDate.now(ZoneId.of(zone));
        List<String> past = planned.stream()
                .filter(p -> p.value() > 0 && p.date().scheduledDate().isBefore(today))
                .map(p -> label(p.source(), p.value()) + " (" + p.date().scheduledDate() + ")")
                .toList();
        if (!past.isEmpty()) {
            throw ApiException.invalid(List.of(new ApiException.FieldError("onStabilityDate", "BACK_ENTRY",
                    "pull dates before today: " + String.join(", ", past) + ". Back-entry of a study that has "
                            + "already started needs an attestation, which isn't available yet")));
        }
    }

    private static String label(ProtocolTimepoint t, long value) {
        return t.label().isBlank() ? value + t.unit().substring(0, 1) : t.label();
    }
}
