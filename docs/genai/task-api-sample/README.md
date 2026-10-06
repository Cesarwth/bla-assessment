# Task API sample (GenAI exercise)

Representative output for the task-management prompt in [`../../genai.md`](../../genai.md): domain, port, service,
REST controller, error handler and the tests that were written first.

It is **documentation, not part of the build**. It was compiled and run inside the backend's test source set
(Java 21, Spring Boot 4.1) before being copied here: **15 tests, all green**, and three deliberate mutations
(remove the owner filter, the due-date check, the status-transition check) were each caught by 2–3 tests.

To run it again, copy the files into `backend/src/test/java/com/ballastlane/assessment/genai/tasks/` and run
`cd backend && ./gradlew test --tests '*genai*'`.
