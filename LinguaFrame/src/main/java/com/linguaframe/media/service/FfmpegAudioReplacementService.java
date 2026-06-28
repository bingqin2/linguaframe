package com.linguaframe.media.service;

import com.linguaframe.media.domain.bo.DubbedVideoBo;
import com.linguaframe.media.domain.bo.ReplaceVideoAudioCommand;

public interface FfmpegAudioReplacementService {

    DubbedVideoBo replaceAudio(ReplaceVideoAudioCommand command);
}
