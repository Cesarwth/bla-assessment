package com.ballastlane.assessment.domain;

import java.util.List;

public record SyncReport(int created, int updated, List<Failure> failures) {

    public SyncReport {
        failures = List.copyOf(failures);
    }

    public record Failure(String identifier, String reason) {
    }
}
