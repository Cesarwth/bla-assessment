package com.ballastlane.assessment.genai.tasks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Test double for the port (the real adapter would be a JPA repository behind the same interface). */
class InMemoryTaskRepository implements TaskRepository {

    private final Map<Long, Task> tasks = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public Task save(Task task) {
        Task stored = task.id() == null
                ? new Task(ids.incrementAndGet(), task.ownerId(), task.title(), task.description(), task.status(), task.dueDate())
                : task;
        tasks.put(stored.id(), stored);
        return stored;
    }

    @Override
    public Optional<Task> findByIdAndOwner(long id, long ownerId) {
        return Optional.ofNullable(tasks.get(id)).filter(task -> task.ownerId() == ownerId);
    }

    @Override
    public List<Task> findAllByOwner(long ownerId) {
        return new ArrayList<>(tasks.values().stream().filter(task -> task.ownerId() == ownerId).toList());
    }

    @Override
    public void delete(Task task) {
        tasks.remove(task.id());
    }

    int count() {
        return tasks.size();
    }
}
