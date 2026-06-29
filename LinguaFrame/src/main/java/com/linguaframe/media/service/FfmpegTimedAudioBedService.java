package com.linguaframe.media.service;

import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.media.domain.bo.CreateTimedAudioBedCommand;

public interface FfmpegTimedAudioBedService {

    TtsResultBo createAudioBed(CreateTimedAudioBedCommand command);
}
