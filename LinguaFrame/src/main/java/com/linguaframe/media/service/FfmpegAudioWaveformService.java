package com.linguaframe.media.service;

import com.linguaframe.media.domain.bo.AudioWaveformAnalyzeCommand;
import com.linguaframe.media.domain.bo.AudioWaveformBo;

public interface FfmpegAudioWaveformService {

    AudioWaveformBo analyze(AudioWaveformAnalyzeCommand command);
}
