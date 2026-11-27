package com.arrheniq.scheduling;

import com.arrheniq.kernel.security.RequirePermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class PullDatePreviewController {

    private final PullDatePreviewService previews;

    PullDatePreviewController(PullDatePreviewService previews) {
        this.previews = previews;
    }

    /** {@code GET /api/v1/protocol-versions/{id}/pull-dates?onStabilityDate=2026-12-01} */
    @GetMapping("/api/v1/protocol-versions/{id}/pull-dates")
    @RequirePermission("masterdata.view")
    public PullDatePreview pullDates(@PathVariable UUID id,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onStabilityDate) {
        return previews.preview(id, onStabilityDate);
    }
}
