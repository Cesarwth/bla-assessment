package com.ballastlane.assessment.infrastructure.persistence;

import java.sql.SQLException;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;

/** Tells a unique-constraint violation apart from other integrity errors, on both H2 and PostgreSQL. */
final class UniqueViolations {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private UniqueViolations() {
    }

    static boolean isUnique(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION_SQL_STATE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    /** Lower-cased messages of the whole cause chain: where the database names the violated constraint. */
    static String details(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            text.append(cause.getMessage()).append(' ');
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }
}
