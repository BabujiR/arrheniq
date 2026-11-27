package com.arrheniq.scheduling;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** A study's schedule. Permissions and site scope are enforced by {@link ScheduleService}. */
@RestController
public class ScheduleController {

    private final ScheduleService schedules;

    ScheduleController(ScheduleService schedules) {
        this.schedules = schedules;
    }

    @GetMapping("/api/v1/studies/{id}/schedule")
    public StudySchedule schedule(@PathVariable("id") UUID id) {
        return schedules.schedule(id);
    }
}
