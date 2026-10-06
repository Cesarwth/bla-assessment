package com.ballastlane.assessment.genai.tasks;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    // Bean Validation only checks the shape of the request; the real rules live in Task and TaskService.
    record CreateTaskRequest(
            @NotBlank @Size(max = Task.MAX_TITLE) String title,
            @Size(max = Task.MAX_DESCRIPTION) String description,
            @JsonProperty("due_date") LocalDate dueDate) {
    }

    record UpdateTaskRequest(
            String title,
            @Size(max = Task.MAX_DESCRIPTION) String description,
            @JsonProperty("due_date") LocalDate dueDate,
            TaskStatus status) {
    }

    record TaskResponse(long id, String title, String description, TaskStatus status, @JsonProperty("due_date") LocalDate dueDate) {
        static TaskResponse from(Task task) {
            return new TaskResponse(task.id(), task.title(), task.description(), task.status(), task.dueDate());
        }
    }

    private final TaskService service;
    private final CurrentUser currentUser;

    public TaskController(TaskService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        Task task = service.create(currentUser.id(), new TaskCommand(request.title(), request.description(), request.dueDate(), null));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(task.id()).toUri();
        return ResponseEntity.created(location).body(TaskResponse.from(task));
    }

    @GetMapping
    List<TaskResponse> list() {
        return service.list(currentUser.id()).stream().map(TaskResponse::from).toList();
    }

    @GetMapping("/{id}")
    TaskResponse get(@PathVariable long id) {
        return TaskResponse.from(service.get(currentUser.id(), id));
    }

    @PatchMapping("/{id}")
    TaskResponse update(@PathVariable long id, @Valid @RequestBody UpdateTaskRequest request) {
        return TaskResponse.from(service.update(currentUser.id(), id,
                new TaskCommand(request.title(), request.description(), request.dueDate(), request.status())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        service.delete(currentUser.id(), id);
    }
}
