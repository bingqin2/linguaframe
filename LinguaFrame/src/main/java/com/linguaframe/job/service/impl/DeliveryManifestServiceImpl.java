package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.DeliveryManifestArtifactVo;
import com.linguaframe.job.domain.vo.DeliveryManifestLinkVo;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class DeliveryManifestServiceImpl implements DeliveryManifestService {

    private static final Set<JobArtifactType> REVIEWED_SUBTITLE_TYPES = EnumSet.of(
            JobArtifactType.REVIEWED_SUBTITLE_JSON,
            JobArtifactType.REVIEWED_SUBTITLE_SRT,
            JobArtifactType.REVIEWED_SUBTITLE_VTT
    );

    private static final Set<JobArtifactType> REVIEWED_HANDOFF_TYPES = EnumSet.of(
            JobArtifactType.REVIEWED_SUBTITLE_JSON,
            JobArtifactType.REVIEWED_SUBTITLE_SRT,
            JobArtifactType.REVIEWED_SUBTITLE_VTT,
            JobArtifactType.REVIEWED_BURNED_VIDEO
    );

    private final LocalizationJobQueryService queryService;
    private final JobArtifactService artifactService;
    private final Clock clock;

    @Autowired
    public DeliveryManifestServiceImpl(
            LocalizationJobQueryService queryService,
            JobArtifactService artifactService
    ) {
        this(queryService, artifactService, Clock.systemUTC());
    }

    public DeliveryManifestServiceImpl(
            LocalizationJobQueryService queryService,
            JobArtifactService artifactService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.artifactService = artifactService;
        this.clock = clock;
    }

    @Override
    public DeliveryManifestVo buildManifest(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);
        List<DeliveryManifestArtifactVo> reviewedArtifacts = artifacts.stream()
                .filter(artifact -> REVIEWED_HANDOFF_TYPES.contains(artifact.type()))
                .map(artifact -> toManifestArtifact(jobId, artifact, "REVIEWED_HANDOFF"))
                .toList();
        List<DeliveryManifestArtifactVo> auditArtifacts = artifacts.stream()
                .filter(artifact -> !REVIEWED_HANDOFF_TYPES.contains(artifact.type()))
                .map(artifact -> toManifestArtifact(jobId, artifact, "AUDIT"))
                .toList();
        int reviewedSubtitleArtifactCount = (int) artifacts.stream()
                .filter(artifact -> REVIEWED_SUBTITLE_TYPES.contains(artifact.type()))
                .count();
        boolean reviewedBurnedVideoAvailable = artifacts.stream()
                .anyMatch(artifact -> artifact.type() == JobArtifactType.REVIEWED_BURNED_VIDEO);
        boolean handoffReady = REVIEWED_SUBTITLE_TYPES.stream()
                .allMatch(type -> artifacts.stream().anyMatch(artifact -> artifact.type() == type));

        return new DeliveryManifestVo(
                job.jobId(),
                job.videoId(),
                job.targetLanguage(),
                job.subtitleStylePreset(),
                job.translationGlossaryEntryCount(),
                job.translationGlossaryHash(),
                job.subtitlePolishingMode(),
                job.status(),
                Instant.now(clock),
                handoffReady,
                reviewedSubtitleArtifactCount,
                reviewedBurnedVideoAvailable,
                auditArtifacts.size(),
                reviewedArtifacts,
                auditArtifacts,
                manifestLinks(jobId)
        );
    }

    @Override
    public String buildMarkdownManifest(String jobId) {
        DeliveryManifestVo manifest = buildManifest(jobId);
        StringBuilder builder = new StringBuilder();
        builder.append("# LinguaFrame Delivery Manifest\n\n");
        builder.append("- Job: ").append(manifest.jobId()).append('\n');
        builder.append("- Video: ").append(manifest.videoId()).append('\n');
        builder.append("- Target language: ").append(manifest.targetLanguage()).append('\n');
        builder.append("- Subtitle style: ").append(manifest.subtitleStylePreset()).append('\n');
        builder.append("- Subtitle polishing: ").append(manifest.subtitlePolishingMode()).append('\n');
        builder.append("- Translation glossary: ").append(manifest.translationGlossaryEntryCount())
                .append(" entries / ")
                .append(manifest.translationGlossaryHash() == null || manifest.translationGlossaryHash().isBlank()
                        ? "none"
                        : manifest.translationGlossaryHash())
                .append('\n');
        builder.append("- Status: ").append(manifest.status()).append('\n');
        builder.append("- Handoff ready: ").append(manifest.handoffReady()).append('\n');
        builder.append("- Reviewed subtitle artifacts: ").append(manifest.reviewedSubtitleArtifactCount()).append('\n');
        builder.append("- Reviewed burned video: ")
                .append(manifest.reviewedBurnedVideoAvailable() ? "Available" : "Not available")
                .append('\n');
        appendArtifacts(builder, "Reviewed Handoff Artifacts", manifest.reviewedArtifacts());
        appendArtifacts(builder, "Audit Artifacts", manifest.auditArtifacts());
        builder.append("\nVerification Links:\n");
        for (DeliveryManifestLinkVo link : manifest.links()) {
            builder.append("- ").append(link.label()).append(": ").append(link.url()).append('\n');
        }
        return builder.toString();
    }

    private DeliveryManifestArtifactVo toManifestArtifact(String jobId, JobArtifactVo artifact, String role) {
        return new DeliveryManifestArtifactVo(
                artifact.artifactId(),
                artifact.type(),
                artifact.filename(),
                artifact.contentType(),
                artifact.sizeBytes(),
                shortHash(artifact.contentSha256()),
                artifact.cacheHit() ? "Reused" : "Generated",
                role,
                "/api/jobs/%s/artifacts/%s/download".formatted(jobId, artifact.artifactId())
        );
    }

    private List<DeliveryManifestLinkVo> manifestLinks(String jobId) {
        return List.of(
                new DeliveryManifestLinkVo("Result bundle", "RESULT_BUNDLE", "/api/jobs/%s/artifacts/archive/download".formatted(jobId)),
                new DeliveryManifestLinkVo("Diagnostics JSON", "DIAGNOSTICS_JSON", "/api/jobs/%s/diagnostics/download".formatted(jobId)),
                new DeliveryManifestLinkVo("Backend evidence", "EVIDENCE_MARKDOWN", "/api/jobs/%s/evidence/markdown/download".formatted(jobId)),
                new DeliveryManifestLinkVo("Evidence bundle", "EVIDENCE_BUNDLE", "/api/jobs/%s/evidence/bundle/download".formatted(jobId))
        );
    }

    private void appendArtifacts(StringBuilder builder, String heading, List<DeliveryManifestArtifactVo> artifacts) {
        builder.append('\n').append(heading).append(":\n");
        if (artifacts.isEmpty()) {
            builder.append("- None\n");
            return;
        }
        for (DeliveryManifestArtifactVo artifact : artifacts) {
            builder.append("- ")
                    .append(artifact.type())
                    .append(": ")
                    .append(artifact.filename())
                    .append(", ")
                    .append(artifact.sizeBytes())
                    .append(" bytes, sha256=")
                    .append(artifact.shortSha256())
                    .append(", ")
                    .append(artifact.downloadUrl())
                    .append('\n');
        }
    }

    private String shortHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "";
        }
        return hash.length() <= 12 ? hash : hash.substring(0, 12);
    }
}
