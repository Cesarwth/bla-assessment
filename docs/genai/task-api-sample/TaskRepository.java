package com.ballastlane.assessment.genai.tasks;

import java.util.List;
import java.util.Optional;

/** Port: the service never sees JPA. Every lookup is scoped by owner, so a missing filter cannot leak data. */
public interface TaskRepository {
    Task save(Task task);

    Optional<Task> findByIdAndOwner(long id, long ownerId);

    List<Task> findAllByOwner(long ownerId);

    void delete(Task task);
}
