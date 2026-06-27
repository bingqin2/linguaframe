package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.bo.TranscriptionCacheLookupBo;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptionCacheHitVo;
import com.linguaframe.job.service.impl.TranscriptSubtitleExportPipelineStage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptSubtitleExportPipelineStageTests {

    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private final RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
    private final RecordingTranscriptionProvider transcriptionProvider = new RecordingTranscriptionProvider();
    private final RecordingTranscriptService transcriptService = new RecordingTranscriptService();
    private final RecordingSubtitleExportService subtitleExportService = new RecordingSubtitleExportService();
    private final RecordingCostBudgetGuardService budgetGuardService = new RecordingCostBudgetGuardService();
    private final RecordingTranscriptionCacheKeyService cacheKeyService = new RecordingTranscriptionCacheKeyService();
    private final RecordingTranscriptionCacheService cacheService = new RecordingTranscriptionCacheService();

    @Test
    void cacheHitSkipsProviderAndBudgetGuardAndWritesTranscriptArtifacts() {
        properties.getTranscription().setEnabled(true);
        properties.getTranscription().setProvider("openai");
        properties.getTranscription().getOpenai().setModel("gpt-4o-transcribe");
        cacheService.hit = new TranscriptionCacheHitVo(
                "transcription-cache-key-stage",
                "source-job-transcription",
                new TranscriptionResultBo(List.of(new TranscriptionSegmentBo(0, 0L, 1_000L, "Cached transcript")))
        );
        LocalizationJobExecutionContextBo context = context("transcript-cache-hit-job");

        stage().execute(context);

        assertThat(cacheKeyService.provider).isEqualTo("OPENAI");
        assertThat(cacheKeyService.model).isEqualTo("gpt-4o-transcribe");
        assertThat(cacheKeyService.promptVersion).isEqualTo("openai-audio-transcriptions-v1");
        assertThat(cacheKeyService.audioContent).containsExactly(7, 8, 9);
        assertThat(transcriptionProvider.called).isFalse();
        assertThat(budgetGuardService.jobId).isNull();
        assertThat(transcriptService.result).isEqualTo(cacheService.hit.result());
        assertThat(context.consumeProviderCacheHits())
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.operation()).isEqualTo(ModelCallOperation.TRANSCRIPTION);
                    assertThat(hit.cacheKey()).isEqualTo("transcription-cache-key-stage");
                    assertThat(hit.sourceJobId()).isEqualTo("source-job-transcription");
                });
        assertThat(artifactService.commands)
                .extracting(CreateJobArtifactCommand::type)
                .containsExactly(
                        JobArtifactType.TRANSCRIPT_JSON,
                        JobArtifactType.SUBTITLE_SRT,
                        JobArtifactType.SUBTITLE_VTT
                );
    }

    @Test
    void cacheMissCallsProviderAndStoresResult() {
        properties.getTranscription().setEnabled(true);
        properties.getTranscription().setProvider("demo");

        stage().execute(context("transcript-cache-miss-job"));

        assertThat(cacheKeyService.provider).isEqualTo("DEMO");
        assertThat(cacheKeyService.model).isEqualTo("demo-transcription");
        assertThat(cacheKeyService.promptVersion).isEqualTo("demo-transcription-v1");
        assertThat(budgetGuardService.jobId).isEqualTo("transcript-cache-miss-job");
        assertThat(budgetGuardService.stage).isEqualTo(LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT);
        assertThat(transcriptionProvider.called).isTrue();
        assertThat(transcriptionProvider.audioContent).containsExactly(7, 8, 9);
        assertThat(cacheService.storedJobId).isEqualTo("transcript-cache-miss-job");
        assertThat(cacheService.storedResult).isEqualTo(transcriptionProvider.result);
        assertThat(artifactService.commands)
                .extracting(CreateJobArtifactCommand::type)
                .containsExactly(
                        JobArtifactType.TRANSCRIPT_JSON,
                        JobArtifactType.SUBTITLE_SRT,
                        JobArtifactType.SUBTITLE_VTT
                );
    }

    private TranscriptSubtitleExportPipelineStage stage() {
        return new TranscriptSubtitleExportPipelineStage(
                properties,
                artifactService,
                transcriptionProvider,
                transcriptService,
                subtitleExportService,
                budgetGuardService,
                cacheKeyService,
                cacheService
        );
    }

    private LocalizationJobExecutionContextBo context(String jobId) {
        Instant now = Instant.parse("2026-06-27T05:00:00Z");
        return new LocalizationJobExecutionContextBo(
                new LocalizationJobRecord(jobId, "transcript-cache-video", "zh-CN", LocalizationJobStatus.PROCESSING, now),
                new QueuedLocalizationJobMessage(
                        jobId,
                        "transcript-cache-video",
                        "source-videos/transcript-cache-video/sample.mp4",
                        "zh-CN",
                        now
                ),
                now
        );
    }

    private static class RecordingTranscriptionCacheKeyService implements TranscriptionCacheKeyService {

        private String provider;
        private String model;
        private String promptVersion;
        private byte[] audioContent;

        @Override
        public TranscriptionCacheLookupBo build(String provider, String model, String promptVersion, byte[] audioContent) {
            this.provider = provider;
            this.model = model;
            this.promptVersion = promptVersion;
            this.audioContent = audioContent;
            return new TranscriptionCacheLookupBo(
                    "transcription-cache-key-stage",
                    "transcription-audio-hash-stage",
                    provider,
                    model,
                    promptVersion
            );
        }
    }

    private static class RecordingTranscriptionCacheService implements TranscriptionCacheService {

        private TranscriptionCacheHitVo hit;
        private String storedJobId;
        private TranscriptionResultBo storedResult;

        @Override
        public Optional<TranscriptionCacheHitVo> findCachedTranscription(TranscriptionCacheLookupBo lookup) {
            return Optional.ofNullable(hit);
        }

        @Override
        public void storeTranscription(TranscriptionCacheLookupBo lookup, String jobId, TranscriptionResultBo result) {
            storedJobId = jobId;
            storedResult = result;
        }
    }

    private static class RecordingCostBudgetGuardService implements CostBudgetGuardService {

        private String jobId;
        private LocalizationJobStage stage;

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
            this.jobId = jobId;
            this.stage = stage;
        }
    }

    private static class RecordingJobArtifactService implements JobArtifactService {

        private final List<CreateJobArtifactCommand> commands = new ArrayList<>();

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            commands.add(command);
            return new JobArtifactVo(
                    "artifact-" + commands.size(),
                    command.jobId(),
                    command.type(),
                    command.filename(),
                    command.contentType(),
                    command.content().length,
                    "content-hash-" + commands.size(),
                    false,
                    null,
                    Instant.parse("2026-06-27T05:01:00Z")
            );
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, JobArtifactRecord source) {
            throw new UnsupportedOperationException("Not needed for this test.");
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return List.of(new JobArtifactVo(
                    "audio-artifact-1",
                    jobId,
                    JobArtifactType.EXTRACTED_AUDIO,
                    "audio.wav",
                    "audio/wav",
                    3L,
                    "audio-hash",
                    false,
                    null,
                    Instant.parse("2026-06-27T05:00:30Z")
            ));
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            return new StoredObjectResourceBo(
                    "audio.wav",
                    "audio/wav",
                    3L,
                    new ByteArrayInputStream(new byte[] {7, 8, 9})
            );
        }
    }

    private static class RecordingTranscriptionProvider implements TranscriptionProvider {

        private boolean called;
        private byte[] audioContent;
        private final TranscriptionResultBo result = new TranscriptionResultBo(
                List.of(new TranscriptionSegmentBo(0, 0L, 1_000L, "Provider transcript"))
        );

        @Override
        public TranscriptionResultBo transcribe(String jobId, byte[] audioContent) {
            called = true;
            this.audioContent = audioContent;
            return result;
        }
    }

    private static class RecordingTranscriptService implements TranscriptService {

        private TranscriptionResultBo result;

        @Override
        public List<TranscriptSegmentVo> replaceTranscript(String jobId, TranscriptionResultBo result) {
            this.result = result;
            return result.segments().stream()
                    .map(segment -> new TranscriptSegmentVo(
                            segment.index(),
                            segment.startMs(),
                            segment.endMs(),
                            segment.text()
                    ))
                    .toList();
        }

        @Override
        public List<TranscriptSegmentVo> listTranscript(String jobId) {
            return List.of();
        }
    }

    private static class RecordingSubtitleExportService implements SubtitleExportService {

        @Override
        public byte[] exportTranscriptJson(List<TranscriptSegmentVo> segments) {
            return "transcript-json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportSrt(List<TranscriptSegmentVo> segments) {
            return "transcript-srt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportVtt(List<TranscriptSegmentVo> segments) {
            return "transcript-vtt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportSubtitleJson(List<com.linguaframe.job.domain.vo.SubtitleSegmentVo> segments) {
            return new byte[0];
        }

        @Override
        public byte[] exportSubtitleSrt(List<com.linguaframe.job.domain.vo.SubtitleSegmentVo> segments) {
            return new byte[0];
        }

        @Override
        public byte[] exportSubtitleVtt(List<com.linguaframe.job.domain.vo.SubtitleSegmentVo> segments) {
            return new byte[0];
        }
    }
}
