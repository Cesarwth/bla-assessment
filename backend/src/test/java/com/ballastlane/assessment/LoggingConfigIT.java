package com.ballastlane.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The environment variables an operator sets (LOG_LEVEL, APP_LOG_LEVEL) really drive the levels, and lines show the request id. */
@SpringBootTest(properties = {"LOG_LEVEL=WARN", "APP_LOG_LEVEL=DEBUG"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LoggingConfigIT {

    @Autowired MockMvc mockMvc;

    @Test
    void logLevelControlsTheRootAndAppLogLevelControlsOurPackage() {
        assertThat(((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).getLevel()).isEqualTo(Level.WARN);
        assertThat(((Logger) LoggerFactory.getLogger("com.ballastlane.assessment")).getLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    void everyConsoleLineOfARequestCarriesItsRequestId(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("X-Request-Id", "trace-42"));

        assertThat(output.getAll()).containsPattern("GET /api/v1/users/me 401 \\d+ms");
        assertThat(output.getAll().lines().filter(line -> line.contains("GET /api/v1/users/me 401")))
                .allSatisfy(line -> assertThat(line).contains("[trace-42]"));
    }
}
