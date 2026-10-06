package com.ballastlane.assessment.genai.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TaskControllerTest {

    static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    InMemoryTaskRepository repository = new InMemoryTaskRepository();
    long currentUser = 1L; // in the real app this comes from the JWT; here the test is the "logged in" user
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        TaskService service = new TaskService(repository, Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new TaskController(service, () -> currentUser))
                .setControllerAdvice(new TaskExceptionHandler())
                .build();
    }

    private String create(String body) throws Exception {
        return mvc.perform(post("/api/v1/tasks").contentType(JSON).content(body))
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    void createsATaskAndReturnsItWithSnakeCaseDueDate() throws Exception {
        mvc.perform(post("/api/v1/tasks").contentType(JSON)
                        .content("{\"title\":\"Buy potions\",\"description\":\"Ten\",\"due_date\":\"2026-10-10\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/tasks/1")))
                .andExpect(jsonPath("$.title").value("Buy potions"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.due_date").value("2026-10-10"));
    }

    @Test
    void rejectsInvalidInputWithOneErrorShape() throws Exception {
        mvc.perform(post("/api/v1/tasks").contentType(JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
        mvc.perform(post("/api/v1/tasks").contentType(JSON).content("{\"title\":\"x\",\"due_date\":\"2020-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TASK"));
        mvc.perform(post("/api/v1/tasks").contentType(JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        // the status can only be changed through PATCH, and an unknown value is a malformed request
        String task = create("{\"title\":\"x\"}");
        mvc.perform(patch(task).contentType(JSON).content("{\"status\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void getsAndListsOnlyTheCallersTasks() throws Exception {
        String mine = create("{\"title\":\"Mine\"}");
        currentUser = 2L;
        create("{\"title\":\"Hers\"}");
        currentUser = 1L;

        mvc.perform(get(mine)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Mine"));
        mvc.perform(get("/api/v1/tasks")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anotherUsersTaskIs404NotForbiddenSoItsExistenceDoesNotLeak() throws Exception {
        String hers = create("{\"title\":\"Hers\"}");
        currentUser = 2L;

        mvc.perform(get(hers)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        mvc.perform(delete(hers)).andExpect(status().isNotFound());
        assertThat(repository.count()).isEqualTo(1);
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

    @Test
    void deletesWith204() throws Exception {
        String task = create("{\"title\":\"Temp\"}");

        mvc.perform(delete(task)).andExpect(status().isNoContent());
        mvc.perform(get(task)).andExpect(status().isNotFound());
    }
}
