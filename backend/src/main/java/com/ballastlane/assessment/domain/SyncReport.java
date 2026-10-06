package com.ballastlane.assessment.domain;

import java.util.List;

/** {@code abortedReason} is null when the whole range was processed. */
public record SyncReport(int created, int updated, List<Failure> failures, String abortedReason) {

    public SyncReport {
        failures = List.copyOf(failures);
    }

    public SyncReport(int created, int updated, List<Failure> failures) {
        this(created, updated, failures, null);
    }

    public record Failure(String identifier, String reason) {
    }
}
