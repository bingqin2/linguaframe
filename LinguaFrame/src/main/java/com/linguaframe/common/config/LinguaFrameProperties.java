package com.linguaframe.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "linguaframe")
public class LinguaFrameProperties {

    @Valid
    private final Media media = new Media();

    @Valid
    private final Worker worker = new Worker();

    @Valid
    private final Cost cost = new Cost();

    public Media getMedia() {
        return media;
    }

    public Worker getWorker() {
        return worker;
    }

    public Cost getCost() {
        return cost;
    }

    public static class Media {

        @Min(1)
        @Max(10240)
        private int maxFileSizeMb = 100;

        @Min(1)
        @Max(86400)
        private int maxDurationSeconds = 120;

        public int getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(int maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }

        public int getMaxDurationSeconds() {
            return maxDurationSeconds;
        }

        public void setMaxDurationSeconds(int maxDurationSeconds) {
            this.maxDurationSeconds = maxDurationSeconds;
        }
    }

    public static class Worker {

        @Min(0)
        @Max(10)
        private int maxRetries = 2;

        @Min(1)
        @Max(86400)
        private int stageTimeoutSeconds = 600;

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getStageTimeoutSeconds() {
            return stageTimeoutSeconds;
        }

        public void setStageTimeoutSeconds(int stageTimeoutSeconds) {
            this.stageTimeoutSeconds = stageTimeoutSeconds;
        }
    }

    public static class Cost {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
