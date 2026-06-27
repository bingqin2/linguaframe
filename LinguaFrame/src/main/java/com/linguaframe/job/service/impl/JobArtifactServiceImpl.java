package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.service.ObjectStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class JobArtifactServiceImpl implements JobArtifactService {

    private final JobArtifactRepository artifactRepository;
    private final ObjectStorageService objectStorageService;
    private final Clock clock;

    @Autowired
    public JobArtifactServiceImpl(JobArtifactRepository artifactRepository, ObjectStorageService objectStorageService) {
        this(artifactRepository, objectStorageService, Clock.systemUTC());
    }

    public JobArtifactServiceImpl(
            JobArtifactRepository artifactRepository,
            ObjectStorageService objectStorageService,
            Clock clock
    ) {
        this.artifactRepository = artifactRepository;
        this.objectStorageService = objectStorageService;
        this.clock = clock;
    }

    @Override
    public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
        String artifactId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now(clock);
        String objectKey = "job-artifacts/%s/%s/%s".formatted(command.jobId(), artifactId, command.filename());
        objectStorageService.store(new StoreObjectCommand(
                objectKey,
                command.contentType(),
                command.content().length,
                new ByteArrayInputStream(command.content())
        ));
        JobArtifactRecord record = new JobArtifactRecord(
                artifactId,
                command.jobId(),
                command.type(),
                objectKey,
                command.filename(),
                command.contentType(),
                command.content().length,
                sha256Hex(command.content()),
                false,
                null,
                createdAt
        );
        artifactRepository.save(record);
        return toVo(record);
    }

    @Override
    public JobArtifactVo createReusedArtifact(String jobId, JobArtifactRecord source) {
        JobArtifactRecord record = new JobArtifactRecord(
                UUID.randomUUID().toString(),
                jobId,
                source.type(),
                source.objectKey(),
                source.filename(),
                source.contentType(),
                source.sizeBytes(),
                source.contentSha256(),
                true,
                source.id(),
                Instant.now(clock)
        );
        artifactRepository.save(record);
        return toVo(record);
    }

    @Override
    public List<JobArtifactVo> listArtifacts(String jobId) {
        return artifactRepository.findByJobId(jobId)
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
        JobArtifactRecord record = artifactRepository.findById(artifactId)
                .filter(artifact -> artifact.jobId().equals(jobId))
                .orElseThrow(() -> new NoSuchElementException("Job artifact not found."));
        return new StoredObjectResourceBo(
                record.filename(),
                record.contentType(),
                record.sizeBytes(),
                objectStorageService.open(record.objectKey())
        );
    }

    private JobArtifactVo toVo(JobArtifactRecord record) {
        return new JobArtifactVo(
                record.id(),
                record.jobId(),
                record.type(),
                record.filename(),
                record.contentType(),
                record.sizeBytes(),
                record.contentSha256(),
                record.cacheHit(),
                record.sourceArtifactId(),
                record.createdAt()
        );
    }

    private String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable.", ex);
        }
    }
}
