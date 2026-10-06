package com.ballastlane.assessment.genai.tasks;

import java.time.LocalDate;

/** For create it describes the new task; for update, a null field means "leave unchanged". */
public record TaskCommand(String title, String description, LocalDate dueDate, TaskStatus status) {
}
