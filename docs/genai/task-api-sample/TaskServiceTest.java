package com.ballastlane.assessment.genai.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final long ASH = 1L;
    private static final long MISTY = 2L;

    InMemoryTaskRepository repository;
    TaskService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTaskRepository();
        service = new TaskService(repository, Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsATaskOwnedByTheCallerStartingAsTodo() {
        Task task = service.create(ASH, new TaskCommand("Buy potions", "Ten of them", TODAY.plusDays(2), null));

        assertThat(task.id()).isNotNull();
        assertThat(task.ownerId()).isEqualTo(ASH);
        assertThat(task.status()).isEqualTo(TaskStatus.TODO);
        assertThat(task.dueDate()).isEqualTo(TODAY.plusDays(2));
    }

    @Test
    void trimsTheTitleAndRejectsABlankOrTooLongOne() {
        assertThat(service.create(ASH, new TaskCommand("  Train  ", null, null, null)).title()).isEqualTo("Train");
        assertThatThrownBy(() -> service.create(ASH, new TaskCommand("   ", null, null, null)))
                .isInstanceOf(InvalidTaskException.class);
        assertThatThrownBy(() -> service.create(ASH, new TaskCommand("x".repeat(121), null, null, null)))
                .isInstanceOf(InvalidTaskException.class);
    }

    @Test
    void rejectsADueDateInThePastButAcceptsToday() {
        assertThatThrownBy(() -> service.create(ASH, new TaskCommand("Late", null, TODAY.minusDays(1), null)))
                .isInstanceOf(InvalidTaskException.class);
        assertThat(service.create(ASH, new TaskCommand("Today", null, TODAY, null)).dueDate()).isEqualTo(TODAY);
    }

    @Test
    void listsOnlyTheCallersTasks() {
        service.create(ASH, new TaskCommand("Mine", null, null, null));
        service.create(MISTY, new TaskCommand("Hers", null, null, null));

        assertThat(service.list(ASH)).extracting(Task::title).containsExactly("Mine");
    }

    @Test
    void anotherUsersTaskLooksLikeItDoesNotExist() {
        Task hers = service.create(MISTY, new TaskCommand("Hers", null, null, null));

        assertThatThrownBy(() -> service.get(ASH, hers.id())).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.update(ASH, hers.id(), new TaskCommand("Stolen", null, null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.delete(ASH, hers.id())).isInstanceOf(TaskNotFoundException.class);
        assertThat(service.get(MISTY, hers.id()).title()).isEqualTo("Hers");
    }

    @Test
    void updatesOnlyTheGivenFields() {
        Task task = service.create(ASH, new TaskCommand("Plan", "Draft", TODAY.plusDays(5), null));

        Task updated = service.update(ASH, task.id(), new TaskCommand(null, "Final", null, null));

        assertThat(updated.title()).isEqualTo("Plan");
        assertThat(updated.description()).isEqualTo("Final");
        assertThat(updated.dueDate()).isEqualTo(TODAY.plusDays(5));
    }

    @Test
    void followsTheStatusWorkflow() {
        Task task = service.create(ASH, new TaskCommand("Ship", null, null, null));

        assertThatThrownBy(() -> service.update(ASH, task.id(), new TaskCommand(null, null, null, TaskStatus.DONE)))
                .isInstanceOf(InvalidTransitionException.class); // must be started first
        assertThat(service.update(ASH, task.id(), new TaskCommand(null, null, null, TaskStatus.IN_PROGRESS)).status())
                .isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(service.update(ASH, task.id(), new TaskCommand(null, null, null, TaskStatus.DONE)).status())
                .isEqualTo(TaskStatus.DONE);
        assertThat(service.update(ASH, task.id(), new TaskCommand(null, null, null, TaskStatus.IN_PROGRESS)).status())
                .isEqualTo(TaskStatus.IN_PROGRESS); // reopening is allowed
    }

    @Test
    void anOldDueDateDoesNotBlockUnrelatedEditsButANewPastDateIsRejected() {
        Task task = repository.save(new Task(null, ASH, "Old", null, TaskStatus.TODO, TODAY.minusDays(10)));

        assertThat(service.update(ASH, task.id(), new TaskCommand("Renamed", null, null, null)).title()).isEqualTo("Renamed");
        assertThatThrownBy(() -> service.update(ASH, task.id(), new TaskCommand(null, null, TODAY.minusDays(3), null)))
                .isInstanceOf(InvalidTaskException.class);
    }

    @Test
    void deletesTheCallersTask() {
        Task task = service.create(ASH, new TaskCommand("Temp", null, null, null));

        service.delete(ASH, task.id());

        assertThatThrownBy(() -> service.get(ASH, task.id())).isInstanceOf(TaskNotFoundException.class);
    }
}
