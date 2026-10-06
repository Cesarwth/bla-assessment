package com.ballastlane.assessment.genai.tasks;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public class TaskService {

    private final TaskRepository repository;
    private final Clock clock;

    public TaskService(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Task create(long ownerId, TaskCommand command) {
        requireNotInThePast(command.dueDate());
        return repository.save(new Task(null, ownerId, command.title(), command.description(), TaskStatus.TODO, command.dueDate()));
    }

    public List<Task> list(long ownerId) {
        return repository.findAllByOwner(ownerId);
    }

    /** Someone else's task and a missing task look the same (404): its existence is not revealed. */
    public Task get(long ownerId, long id) {
        return repository.findByIdAndOwner(id, ownerId).orElseThrow(() -> new TaskNotFoundException(id));
    }

    public Task update(long ownerId, long id, TaskCommand command) {
        Task current = get(ownerId, id);
        // Only a date the user is changing has to be in the future: an old, already overdue task stays editable.
        if (command.dueDate() != null && !command.dueDate().equals(current.dueDate())) {
            requireNotInThePast(command.dueDate());
        }
        TaskStatus status = command.status() == null ? current.status() : command.status();
        if (!current.status().canMoveTo(status)) {
            throw new InvalidTransitionException(current.status(), status);
        }
        return repository.save(new Task(
                current.id(),
                ownerId,
                command.title() == null ? current.title() : command.title(),
                command.description() == null ? current.description() : command.description(),
                status,
                command.dueDate() == null ? current.dueDate() : command.dueDate()));
    }

    public void delete(long ownerId, long id) {
        repository.delete(get(ownerId, id));
    }

    private void requireNotInThePast(LocalDate dueDate) {
        if (dueDate != null && dueDate.isBefore(LocalDate.now(clock))) {
            throw new InvalidTaskException("The due date cannot be in the past");
        }
    }
}
