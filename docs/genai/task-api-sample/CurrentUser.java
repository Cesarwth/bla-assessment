package com.ballastlane.assessment.genai.tasks;

/** Who is calling. The real app builds it from the JWT claims; tests supply a fixed one. */
@FunctionalInterface
public interface CurrentUser {
    long id();
}
