# Generative AI exercise — task-management API

The brief asks for: **(1)** the prompt I would use to generate a task-management API, **(2)** the output (or a representative sample), and **(3)** how I validated it, what I corrected or improved, and how I handled edge cases, authentication and validation.

Everything below is real: the sample was written **test-first with Claude Code**, compiled and run in this repository's backend (Java 21, Spring Boot 4.1) before being quoted — **15 tests, all green** — and it follows the same Clean Architecture rules as the Pokémon API. The full source is in [`docs/genai/task-api-sample/`](genai/task-api-sample/). Section 4 lists real mistakes made by the AI (and by me) while building *this* project, because those say more about working with AI than a polished story.

## 1. The prompt

I treat a prompt like a work order for a new teammate: context, non-negotiable constraints, the business rules, the edge cases to prove, and *how to work* (tests first, show real output, ask when unsure). One long prompt beats a chain of vague ones.

```text
You are a senior Java engineer working in an existing Spring Boot 4 / Java 21 / Gradle project that follows
Clean Architecture (packages: domain / application / infrastructure), has a JWT-secured API, and a User model
with a numeric id. Build a RESTful task-management API.

## What it must do
- Create, read (one and a list), update (partial) and delete tasks.
- A task has: title, description, status, due_date (the JSON field is named "due_date").
- A task belongs to exactly one user. Users only ever see and change their own tasks.

## Non-negotiable constraints
- Clean Architecture: domain and application must NOT import Spring, JPA or Jackson. The persistence port is
  defined in application; infrastructure implements it. Use cases are plain Java, wired in one config class.
- Test-first: for every piece, write the failing test, run it and show me the red output, then write the
  minimum code to pass, run again, then refactor. Never write production code without a failing test.
- Validation: shape checks (required, sizes) in the request DTO with Bean Validation; BUSINESS rules in the
  domain/service so they hold for every entry point, not just this controller.
- Errors: one JSON error shape {status, code, message, fieldErrors} with correct status codes
  (400/401/403/404/409). No stack traces or internal messages in responses.
- Security: the caller's identity comes ONLY from the JWT, never from the body, a header or a query param.
  Another user's task must behave exactly like a missing one (404), so its existence is not revealed.

## Business rules
- title: trimmed, 1..120 characters. description: at most 2000.
- due_date must not be in the past when it is being set or changed (today is fine). An already-overdue task
  must stay editable: only validate a due date the user is actually changing.
- status workflow: TODO -> IN_PROGRESS -> DONE; a finished task may be reopened (DONE -> IN_PROGRESS);
  steps cannot be skipped (TODO -> DONE is refused with 409).

## Edge cases that must each have a test
blank, whitespace-only and too-long title · past due date · today as due date · malformed JSON · unknown
status value · partial update leaves the other fields untouched · get/update/delete on another user's task ·
empty list · deleting twice · unauthenticated request.

## How to work
1. Before writing code, list your assumptions and any open questions. If something is ambiguous, ask.
2. Work in phases of at most five files: domain+rules, service+port, persistence adapter, web layer, end-to-end.
3. After each phase run the tests and report the REAL output. Do not claim anything works unless you ran it.
4. When done, act as an adversarial reviewer of your own work: list possible bugs, security holes and missing
   tests, and for each one propose a failing test that would expose it.
```

Follow-up prompts I use after the first pass: *"Review this diff as a skeptical senior engineer; for each finding give file:line and a failing test"*, *"What would break silently?"*, and *"Which of your claims did you not verify by running something?"*.

## 2. Output (representative sample)

Domain — the workflow lives with the status, and invariants live in the entity so no entry point can build an invalid task:

```java
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
```

```java
import java.time.LocalDate;

/** A task belongs to exactly one user. Invariants live here so no entry point can build an invalid one. */
public record Task(Long id, long ownerId, String title, String description, TaskStatus status, LocalDate dueDate) {

    static final int MAX_TITLE = 120;
    static final int MAX_DESCRIPTION = 2000;

    public Task {
        title = title == null ? "" : title.strip();
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw new InvalidTaskException("Title must have 1 to " + MAX_TITLE + " characters");
        }
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new InvalidTaskException("Description must have at most " + MAX_DESCRIPTION + " characters");
        }
        status = status == null ? TaskStatus.TODO : status;
    }
}
```

Application — the use case. Note the two decisions that came from the prompt: another user's task is *not found*, and only a date the user is **changing** has to be in the future:

```java
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
```

Web — the controller only translates HTTP (shape validation in the DTOs, identity from `CurrentUser`, which the real app builds from the JWT), and one handler gives every failure the same shape:

```java
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
```

```java
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** One error shape for every failure: {status, code, message, fieldErrors}. */
@RestControllerAdvice
public class TaskExceptionHandler {

    record FieldProblem(String field, String message) {
    }

    record ErrorBody(int status, String code, String message, List<FieldProblem> fieldErrors) {
    }

    private static ResponseEntity<ErrorBody> body(HttpStatus status, String code, String message, List<FieldProblem> fields) {
        return ResponseEntity.status(status).body(new ErrorBody(status.value(), code, message, fields));
    }

    @ExceptionHandler(InvalidTaskException.class)
    ResponseEntity<ErrorBody> invalid(InvalidTaskException e) {
        return body(HttpStatus.BAD_REQUEST, "INVALID_TASK", e.getMessage(), List.of());
    }

    @ExceptionHandler(TaskNotFoundException.class)
    ResponseEntity<ErrorBody> notFound(TaskNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", e.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ResponseEntity<ErrorBody> transition(InvalidTransitionException e) {
        return body(HttpStatus.CONFLICT, "INVALID_TRANSITION", e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e) {
        List<FieldProblem> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldProblem(f.getField(), f.getDefaultMessage())).toList();
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> malformed(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed", List.of());
    }
}
```

Tests written first (excerpts; the sample has 9 service tests and 6 controller tests):

```java
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
    void anOldDueDateDoesNotBlockUnrelatedEditsButANewPastDateIsRejected() {
        Task task = repository.save(new Task(null, ASH, "Old", null, TaskStatus.TODO, TODAY.minusDays(10)));

        assertThat(service.update(ASH, task.id(), new TaskCommand("Renamed", null, null, null)).title()).isEqualTo("Renamed");
        assertThatThrownBy(() -> service.update(ASH, task.id(), new TaskCommand(null, null, TODAY.minusDays(3), null)))
                .isInstanceOf(InvalidTaskException.class);
    }

    @Test
    void updatesPartiallyAndEnforcesTheStatusWorkflowWith409() throws Exception {
        String task = create("{\"title\":\"Ship\"}");

        mvc.perform(patch(task).contentType(JSON).content("{\"description\":\"Soon\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Soon"))
                .andExpect(jsonPath("$.title").value("Ship"));
        mvc.perform(patch(task).contentType(JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        mvc.perform(patch(task).contentType(JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }
```

## 3. How I validated, corrected and hardened it

### Validating the AI's output

1. **Read every line.** I do not accept code I cannot explain line by line in a review.
2. **Tests first, and watch them fail.** A test that never failed proves nothing. For each phase the red run came first (here: 58 compile errors, then real assertion failures once skeletons existed).
3. **Run, do not assume.** The sample was executed (`./gradlew test --tests '*genai*'`): 15 tests, 0 failures.
4. **Mutation checks — prove the tests can fail.** I broke the code on purpose and confirmed the suite notices:

   | Deliberate bug | Tests that failed |
   | --- | --- |
   | repository stops filtering by owner | `anotherUsersTaskLooksLikeItDoesNotExist`, `anotherUsersTaskIs404NotForbiddenSoItsExistenceDoesNotLeak` |
   | due-date check never fires | `rejectsADueDateInThePastButAcceptsToday`, `anOldDueDateDoesNotBlockUnrelatedEditsButANewPastDateIsRejected`, `rejectsInvalidInputWithOneErrorShape` |
   | status workflow not enforced | `followsTheStatusWorkflow`, `updatesPartiallyAndEnforcesTheStatusWorkflowWith409` |

5. **Check against the requirements list**, not against the code: every rule in the prompt maps to at least one test (table below).
6. **Verify claims about the outside world** (library versions, framework behavior) by running something, never from memory — see section 4.

### Corrected or improved

- **A wrong test, not wrong code.** My first controller test expected `400` when POSTing `"status": "NOPE"`. It got `201`: the create DTO has no `status` field, so Jackson ignores it. The code was right (the status can only change through `PATCH`); the test aimed at the wrong endpoint. I moved the check to `PATCH`, where it returns `400 MALFORMED_REQUEST`.
- **Decisions I make deliberately instead of accepting defaults** (each is a design choice in the sample, with a test): `404` instead of `403` for someone else's task (a `403` confirms the id exists); validating the due date only when it changes (otherwise an overdue task could never be edited, even to mark it done); rules in the domain, shape checks in the DTO; DTOs instead of exposing entities; identity from the token only; a single error shape.

### Edge cases, authentication and validation

| Concern | How it is handled | Test |
| --- | --- | --- |
| Authentication | identity comes from the JWT via `CurrentUser` (in the real app: the `uid` claim, see `ActorResolver`); no endpoint takes a user id from the client | controller tests run as a fixed current user; the main project's `SecurityRulesMatrixTest` covers `401` without a token |
| Authorization | every repository lookup is scoped by owner; another user's task is `404` for get, update and delete | `anotherUsersTaskLooksLikeItDoesNotExist` |
| Shape validation | Bean Validation on the DTO → `400 VALIDATION_FAILED` with `fieldErrors` | `rejectsInvalidInputWithOneErrorShape` |
| Business validation | blank/long title, description size, past due date → `400 INVALID_TASK` | `trimsTheTitleAndRejectsABlankOrTooLongOne`, `rejectsADueDateInThePastButAcceptsToday` |
| Workflow | `TODO → DONE` refused with `409 INVALID_TRANSITION`; reopening allowed | `followsTheStatusWorkflow` |
| Partial update | `null` = unchanged | `updatesOnlyTheGivenFields` |
| Overdue tasks | stay editable; only a *changed* date is checked | `anOldDueDateDoesNotBlockUnrelatedEditsButANewPastDateIsRejected` |
| Malformed input | broken JSON or an unknown enum value → `400 MALFORMED_REQUEST` | `rejectsInvalidInputWithOneErrorShape` |
| Error leakage | one handler, fixed messages | same |

What I would add for production and did not include in the sample: pagination and sorting on the list, optimistic locking (the Pokémon API in this repository does it with a `version`), a JPA adapter with a migration, and rate limiting.

## 4. Critical thinking: real mistakes found while building this project

AI-generated code and AI-generated claims need verification. These are real cases from this repository (see the commit history and `docs/`):

| What went wrong | Who | How it was caught | Outcome |
| --- | --- | --- | --- |
| A password longer than 72 **bytes** made BCrypt throw, turning registration into a `500`; the length check counted characters | AI-written code | an independent reviewer flagged it; I **reproduced it with a short program** (73 ASCII bytes and 60 accented characters both threw) before trusting the claim | check moved to bytes in the domain, tests added |
| Re-syncing a Pokémon could **wipe** stored description/evolution when PokeAPI answered partially | AI-written code | reviewer; traced by hand through `refreshedFrom` | stored values are kept when upstream returns none (test first) |
| The browser got `403 Invalid CORS request` on every `POST`/`PATCH` through nginx | AI-written config | **only a real Chrome** run showed it; jsdom tests and `curl` could not (no `Origin` header) | `Host $http_host` in nginx; `scripts/smoke.sh` now sends `Origin` like a browser |
| A reviewer claimed `@Cacheable(unless="#result == null")` does not work with `Optional` | AI **reviewer** | an existing test (`notFoundIsNotCached`) already proved the opposite | claim discarded — reviews are inputs, not verdicts |
| I wrote "build green" in a plan before it was true: a shared in-memory test database made one test order-dependent | me | the next full `clean build` failed | one database per test context; plan corrected |
| I picked the newest majors of the frontend toolchain; `typescript-eslint` did not support TypeScript 7 and npm crashed on Vitest 4 | AI recommendation | install and peer checks | moved to stable lines (React 19, Vite 7, TypeScript 5.9), later Vitest 5 which also cleared `npm audit` |
| Assertions that could not fail, a `Thread.sleep` for timing, a test asserting the wrong header | AI-written tests | review, and refusing to trust a test I had not seen fail | rewritten (latches, mutation checks) |
| A container scan found **3 critical CVEs in Tomcat** and 5 high in Jackson inside the jar | the framework's managed versions | scanning the built image, not just the source | pinned patched versions with a removal note |

Habits that came out of it: reproduce before fixing; make a test fail before believing it; check the real thing (browser, image, database) and not only the mock; treat reviewer output as hypotheses; write down what was *not* verified.

## 5. The workflow used for the project itself

- **Research → plan → implement → code review**, each as a Claude Code skill (`.claude/`): research maps the code and the external contract (e.g. the real PokeAPI payloads), the plan lists the tests to write first per phase, implement runs red/green/refactor and records deviations, review runs four specialist reviewers in parallel (architecture, correctness, security, tests). Plans and research are kept in [`docs/`](.).
- **Conventions in `CLAUDE.md`** (architecture rules, TDD policy, error shape, frontend rules) so every session starts from the same rules.
- **Humans decide, AI executes and verifies.** Product decisions were mine: who may edit (owner or admin), that PATCH must carry a version, no rate limiting ("it is a game, not a bank"), one repository with `backend/` and `frontend/`, which vulnerabilities matter, and publishing under my personal account. The AI proposed options with trade-offs, wrote tests and code, and ran the checks (build, smoke script, Chrome DevTools pass, image scans).
- **Verification beyond unit tests**: `scripts/smoke.sh`, a Chrome pass (desktop, mobile, dark, logged in) reading the real console, and Trivy on both images.
