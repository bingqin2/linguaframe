package com.linguaframe.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    @Valid
    private final Database database = new Database();

    @Valid
    private final Redis redis = new Redis();

    @Valid
    private final Rabbitmq rabbitmq = new Rabbitmq();

    @Valid
    private final Storage storage = new Storage();

    @Valid
    private final Ffmpeg ffmpeg = new Ffmpeg();

    @Valid
    private final Transcription transcription = new Transcription();

    public Media getMedia() {
        return media;
    }

    public Worker getWorker() {
        return worker;
    }

    public Cost getCost() {
        return cost;
    }

    public Database getDatabase() {
        return database;
    }

    public Redis getRedis() {
        return redis;
    }

    public Rabbitmq getRabbitmq() {
        return rabbitmq;
    }

    public Storage getStorage() {
        return storage;
    }

    public Ffmpeg getFfmpeg() {
        return ffmpeg;
    }

    public Transcription getTranscription() {
        return transcription;
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

        private boolean dispatchEnabled = false;

        @Min(1)
        @Max(1000)
        private int dispatchBatchSize = 10;

        @Min(100)
        @Max(600000)
        private long dispatchIntervalMs = 5000L;

        private boolean executionEnabled = false;

        @Min(0)
        @Max(60000)
        private long smokeStageDurationMs = 0L;

        private boolean smokeStageFailureEnabled = false;

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

        public boolean isDispatchEnabled() {
            return dispatchEnabled;
        }

        public void setDispatchEnabled(boolean dispatchEnabled) {
            this.dispatchEnabled = dispatchEnabled;
        }

        public int getDispatchBatchSize() {
            return dispatchBatchSize;
        }

        public void setDispatchBatchSize(int dispatchBatchSize) {
            this.dispatchBatchSize = dispatchBatchSize;
        }

        public long getDispatchIntervalMs() {
            return dispatchIntervalMs;
        }

        public void setDispatchIntervalMs(long dispatchIntervalMs) {
            this.dispatchIntervalMs = dispatchIntervalMs;
        }

        public boolean isExecutionEnabled() {
            return executionEnabled;
        }

        public void setExecutionEnabled(boolean executionEnabled) {
            this.executionEnabled = executionEnabled;
        }

        public long getSmokeStageDurationMs() {
            return smokeStageDurationMs;
        }

        public void setSmokeStageDurationMs(long smokeStageDurationMs) {
            this.smokeStageDurationMs = smokeStageDurationMs;
        }

        public boolean isSmokeStageFailureEnabled() {
            return smokeStageFailureEnabled;
        }

        public void setSmokeStageFailureEnabled(boolean smokeStageFailureEnabled) {
            this.smokeStageFailureEnabled = smokeStageFailureEnabled;
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

    public static class Ffmpeg {

        @NotBlank
        private String binaryPath = "ffmpeg";

        private boolean audioEnabled = false;

        @Min(1)
        @Max(3600)
        private int audioTimeoutSeconds = 120;

        @NotBlank
        private String workDir = "/tmp/linguaframe-media";

        public String getBinaryPath() {
            return binaryPath;
        }

        public void setBinaryPath(String binaryPath) {
            this.binaryPath = binaryPath;
        }

        public boolean isAudioEnabled() {
            return audioEnabled;
        }

        public void setAudioEnabled(boolean audioEnabled) {
            this.audioEnabled = audioEnabled;
        }

        public int getAudioTimeoutSeconds() {
            return audioTimeoutSeconds;
        }

        public void setAudioTimeoutSeconds(int audioTimeoutSeconds) {
            this.audioTimeoutSeconds = audioTimeoutSeconds;
        }

        public String getWorkDir() {
            return workDir;
        }

        public void setWorkDir(String workDir) {
            this.workDir = workDir;
        }
    }

    public static class Transcription {

        private boolean enabled = false;

        @NotBlank
        private String provider = "demo";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    public static class Database {

        @NotBlank
        private String host = "localhost";

        @Min(1)
        @Max(65535)
        private int port = 3306;

        @NotBlank
        private String name = "linguaframe";

        @NotBlank
        private String username = "linguaframe";

        @NotBlank
        private String password = "linguaframe_dev_password";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Redis {

        @NotBlank
        private String host = "localhost";

        @Min(1)
        @Max(65535)
        private int port = 6379;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class Rabbitmq {

        @NotBlank
        private String host = "localhost";

        @Min(1)
        @Max(65535)
        private int port = 5672;

        @NotBlank
        private String username = "linguaframe";

        @NotBlank
        private String password = "linguaframe_dev_password";

        @NotBlank
        private String jobExchange = "linguaframe.jobs";

        @NotBlank
        private String jobQueue = "linguaframe.localization.jobs";

        @NotBlank
        private String jobRoutingKey = "localization.queued";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getJobExchange() {
            return jobExchange;
        }

        public void setJobExchange(String jobExchange) {
            this.jobExchange = jobExchange;
        }

        public String getJobQueue() {
            return jobQueue;
        }

        public void setJobQueue(String jobQueue) {
            this.jobQueue = jobQueue;
        }

        public String getJobRoutingKey() {
            return jobRoutingKey;
        }

        public void setJobRoutingKey(String jobRoutingKey) {
            this.jobRoutingKey = jobRoutingKey;
        }
    }

    public static class Storage {

        @NotBlank
        private String endpoint = "http://localhost:9000";

        @NotBlank
        private String bucket = "linguaframe-artifacts";

        @NotBlank
        private String accessKey = "linguaframe";

        @NotBlank
        private String secretKey = "linguaframe_minio_password";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }
}
