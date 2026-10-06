package com.ballastlane.assessment.genai.tasks;

import java.time.LocalDate;

/** A task belongs to exactly one user. Invariants live here so no entry point can build an invalid one. */
public record Task(Long id, long ownerId, String title, String description, TaskStatus status, LocalDate dueDate) {

    static final int MAX_TITLE = 120;
    static final int MAX_DESCRIPTION = 2000;

    public Task {
        title = title == null ? "" : title.strip();
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw new InvalidTaskException("Title must have 1 to " + MAX_TITLE + " characters");
        }
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new InvalidTaskException("Description must have at most " + MAX_DESCRIPTION + " characters");
        }
        status = status == null ? TaskStatus.TODO : status;
    }
}
