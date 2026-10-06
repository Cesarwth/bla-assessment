package com.ballastlane.assessment.genai.tasks;

class InvalidTaskException extends RuntimeException {
    InvalidTaskException(String message) {
        super(message);
    }
}

class InvalidTransitionException extends RuntimeException {
    InvalidTransitionException(TaskStatus from, TaskStatus to) {
        super("A task cannot move from " + from + " to " + to);
    }
}

class TaskNotFoundException extends RuntimeException {
    TaskNotFoundException(long id) {
        super("Task " + id + " was not found");
    }
}
