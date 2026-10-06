package com.ballastlane.assessment.infrastructure.web.dto;

import com.ballastlane.assessment.domain.SyncReport;
import java.util.List;

/** {@code abortedReason} is null unless the run stopped early (e.g. PokeAPI went down midway). */
public record SyncReportResponse(int created, int updated, List<FailureResponse> failures, String abortedReason) {

    public record FailureResponse(String identifier, String reason) {
    }

    public static SyncReportResponse from(SyncReport report) {
        return new SyncReportResponse(report.created(), report.updated(),
                report.failures().stream().map(f -> new FailureResponse(f.identifier(), f.reason())).toList(),
                report.abortedReason());
    }
}
