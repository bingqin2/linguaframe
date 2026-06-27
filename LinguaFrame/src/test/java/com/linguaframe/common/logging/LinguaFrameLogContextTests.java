package com.linguaframe.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class LinguaFrameLogContextTests {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void jobContextSetsAndClearsSafeIdentifiers() {
        try (AutoCloseable ignored = LinguaFrameLogContext.withJob("job-123", "video-456")) {
            assertThat(MDC.get("jobId")).isEqualTo("job-123");
            assertThat(MDC.get("videoId")).isEqualTo("video-456");
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }

        assertThat(MDC.get("jobId")).isNull();
        assertThat(MDC.get("videoId")).isNull();
    }

    @Test
    void nestedStageContextRestoresPreviousValues() {
        MDC.put("stage", "PREVIOUS_STAGE");
        MDC.put("workerRole", "PREVIOUS_ROLE");

        try (AutoCloseable ignored = LinguaFrameLogContext.withStage("WORKER_SMOKE", "COMBINED")) {
            assertThat(MDC.get("stage")).isEqualTo("WORKER_SMOKE");
            assertThat(MDC.get("workerRole")).isEqualTo("COMBINED");
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }

        assertThat(MDC.get("stage")).isEqualTo("PREVIOUS_STAGE");
        assertThat(MDC.get("workerRole")).isEqualTo("PREVIOUS_ROLE");
    }

    @Test
    void blankValuesAreNotWritten() {
        try (AutoCloseable ignored = LinguaFrameLogContext.withJob(" ", null)) {
            assertThat(MDC.get("jobId")).isNull();
            assertThat(MDC.get("videoId")).isNull();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
