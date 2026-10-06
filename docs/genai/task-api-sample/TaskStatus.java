package com.ballastlane.assessment.genai.tasks;

/** Workflow: TODO -> IN_PROGRESS -> DONE, and a finished task can be reopened. Skipping a step is not allowed. */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE;

    public boolean canMoveTo(TaskStatus next) {
        return switch (this) {
            case TODO -> next == TODO || next == IN_PROGRESS;
            case IN_PROGRESS -> true;
            case DONE -> next == DONE || next == IN_PROGRESS;
        };
    }
}
