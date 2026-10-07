package com.ballastlane.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Production sets LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs: one JSON object per line, request id as its own field. */
@SpringBootTest(properties = "logging.structured.format.console=ecs")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingIT {

    @Autowired MockMvc mockMvc;

    @Test
    void theAccessLineIsAJsonObjectCarryingTheRequestId(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("X-Request-Id", "json-7"));

        String line = output.getAll().lines().filter(candidate -> candidate.contains("GET /api/v1/users/me 401")).findFirst()
                .orElseThrow();
        assertThat(line).startsWith("{").endsWith("}").contains("\"requestId\":\"json-7\"");
    }
}
