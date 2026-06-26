package com.linguaframe.media.service;

import com.linguaframe.media.domain.bo.ExtractAudioCommand;
import com.linguaframe.media.domain.bo.ExtractedAudioBo;

public interface FfmpegAudioExtractionService {

    ExtractedAudioBo extractAudio(ExtractAudioCommand command);
}
