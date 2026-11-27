package com.arrheniq.scheduling;

import com.arrheniq.kernel.security.RequirePermission;
import com.arrheniq.study.StudyDetails;
import com.arrheniq.study.StudyService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Reading a study's schedule (URS-SCH-001/004/007). Visibility follows the study (site scope). */
@Service
public class ScheduleService {

    private final JdbcClient jdbc;
    private final StudyService studies;

    ScheduleService(JdbcClient jdbc, StudyService studies) {
        this.jdbc = jdbc;
        this.studies = studies;
    }

    @RequirePermission("schedule.view")
    @Transactional(readOnly = true)
    public StudySchedule schedule(UUID studyId) {
        StudyDetails study = studies.get(studyId);      // 404 when not visible
        List<StudyTimepointView> timepoints = jdbc.sql("""
                SELECT id, seq, COALESCE(label, '') AS label, value, unit, calculated_date, scheduled_date,
                       date_rule_applied, window_days_before, window_days_after, units_planned, status
                  FROM app.study_timepoint
                 WHERE study_id = ? AND deleted_at IS NULL
                 ORDER BY seq
                """)
            .param(studyId)
            .query((rs, _) -> {
                LocalDate pull = rs.getObject("scheduled_date", LocalDate.class);
                return new StudyTimepointView(rs.getObject("id", UUID.class), rs.getInt("seq"), rs.getString("label"),
                        rs.getBigDecimal("value").longValue(), rs.getString("unit"),
                        rs.getObject("calculated_date", LocalDate.class), pull, rs.getString("date_rule_applied"),
                        pull.minusDays(rs.getInt("window_days_before")), pull.plusDays(rs.getInt("window_days_after")),
                        rs.getInt("units_planned"), rs.getString("status"));
            })
            .list();
        return new StudySchedule(studyId, study.scheduleAlgorithm(), timepoints);
    }
}
