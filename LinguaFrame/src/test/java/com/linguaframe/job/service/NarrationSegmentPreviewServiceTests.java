package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.dto.PreviewNarrationSegmentRequestDto;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationSegmentPreviewVo;
import com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo;
import com.linguaframe.job.domain.vo.NarrationVoicePresetVo;
import com.linguaframe.job.service.impl.NarrationSegmentPreviewServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrationSegmentPreviewServiceTests {

    @Test
    void rejectsBlankTextBeforeCallingProvider() {
        RecordingTtsProvider ttsProvider = new RecordingTtsProvider();
        NarrationSegmentPreviewService service = service(ttsProvider, "verse");

        assertThatThrownBy(() -> service.previewSegment(
                "job-preview",
                new PreviewNarrationSegmentRequestDto("   ", "alloy")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration preview text is required");

        assertThat(ttsProvider.requests).isEmpty();
    }

    @Test
    void passesExplicitVoiceLanguageAndTrimmedTextToProvider() {
        RecordingTtsProvider ttsProvider = new RecordingTtsProvider();
        NarrationSegmentPreviewService service = service(ttsProvider, "verse");

        NarrationSegmentPreviewVo result = service.previewSegment(
                "job-preview",
                new PreviewNarrationSegmentRequestDto("  Preview this narration.  ", "alloy")
        );

        assertThat(ttsProvider.requests).hasSize(1);
        TtsRequestBo request = ttsProvider.requests.getFirst();
        assertThat(request.jobId()).isEqualTo("job-preview");
        assertThat(request.language()).isEqualTo("zh-CN");
        assertThat(request.voice()).isEqualTo("alloy");
        assertThat(request.text()).isEqualTo("Preview this narration.");
        assertThat(result.audioContent()).containsExactly(7, 8, 9);
        assertThat(result.filename()).isEqualTo("narration-segment-preview.mp3");
        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        assertThat(result.voice()).isEqualTo("alloy");
        assertThat(result.characterCount()).isEqualTo(23);
        assertThat(result.safetyNote()).contains("does not create artifacts");
    }

    @Test
    void blankVoiceInheritsJobVoiceThenCatalogDefault() {
        RecordingTtsProvider jobVoiceProvider = new RecordingTtsProvider();
        service(jobVoiceProvider, "verse").previewSegment(
                "job-preview",
                new PreviewNarrationSegmentRequestDto("Use job voice.", " ")
        );

        assertThat(jobVoiceProvider.requests).extracting(TtsRequestBo::voice)
                .containsExactly("verse");

        RecordingTtsProvider defaultVoiceProvider = new RecordingTtsProvider();
        service(defaultVoiceProvider, null).previewSegment(
                "job-preview",
                new PreviewNarrationSegmentRequestDto("Use default voice.", null)
        );

        assertThat(defaultVoiceProvider.requests).extracting(TtsRequestBo::voice)
                .containsExactly("demo-voice");
    }

    @Test
    void rejectsUnknownVoiceAndExcessiveVoiceBeforeCallingProvider() {
        RecordingTtsProvider ttsProvider = new RecordingTtsProvider();
        NarrationSegmentPreviewService service = service(ttsProvider, "verse");

        assertThatThrownBy(() -> service.previewSegment(
                "job-preview",
                new PreviewNarrationSegmentRequestDto("Preview this.", "unknown")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration preview voice must be one of the configured presets");

        assertThatThrownBy(() -> service.previewSegment(
                "job-preview",
                new PreviewNarrationSegmentRequestDto("Preview this.", "x".repeat(65))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Narration preview voice must be 64 characters or fewer");

        assertThat(ttsProvider.requests).isEmpty();
    }

    @Test
    void fallsBackToAudioMpegWhenProviderContentTypeIsBlank() {
        RecordingTtsProvider ttsProvider = new RecordingTtsProvider();
        ttsProvider.result = new TtsResultBo(new byte[] {1, 2, 3}, "provider.wav", " ");

        NarrationSegmentPreviewVo result = service(ttsProvider, "verse").previewSegment(
                "job-preview",
                new PreviewNarrationSegmentRequestDto("Preview this.", "alloy")
        );

        assertThat(result.contentType()).isEqualTo("audio/mpeg");
    }

    private static NarrationSegmentPreviewService service(RecordingTtsProvider ttsProvider, String jobVoice) {
        return new NarrationSegmentPreviewServiceImpl(
                new StaticLocalizationJobQueryService("zh-CN", jobVoice),
                ttsProvider,
                new StaticNarrationVoiceCatalogService()
        );
    }

    private static final class RecordingTtsProvider implements TtsProvider {

        private final List<TtsRequestBo> requests = new ArrayList<>();
        private TtsResultBo result = new TtsResultBo(new byte[] {7, 8, 9}, "provider-file.mp3", "audio/mpeg");

        @Override
        public TtsResultBo synthesize(TtsRequestBo request) {
            requests.add(request);
            return result;
        }
    }

    private static final class StaticNarrationVoiceCatalogService implements NarrationVoiceCatalogService {

        @Override
        public NarrationVoiceCatalogVo catalog() {
            return new NarrationVoiceCatalogVo(
                    "demo",
                    "demo-voice",
                    List.of(
                            new NarrationVoicePresetVo("demo-voice", "Demo voice", "demo", true, "Default demo voice."),
                            new NarrationVoicePresetVo("alloy", "Alloy", "openai", false, "OpenAI alloy voice."),
                            new NarrationVoicePresetVo("verse", "Verse", "openai", false, "OpenAI verse voice.")
                    ),
                    List.of()
            );
        }
    }

    private record StaticLocalizationJobQueryService(String targetLanguage, String ttsVoice)
            implements LocalizationJobQueryService {

        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return new LocalizationJobVo(
                    jobId,
                    "video-" + jobId,
                    targetLanguage,
                    ttsVoice,
                    LocalizationJobStatus.COMPLETED,
                    Instant.parse("2026-06-30T00:00:00Z"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    null,
                    0,
                    null,
                    List.of(),
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null
            );
        }

        @Override
        public com.linguaframe.job.domain.vo.JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }
}
