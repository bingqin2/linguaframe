package com.linguaframe.media.service;

import com.linguaframe.media.domain.bo.DubbedVideoBo;
import com.linguaframe.media.domain.bo.MixNarratedVideoCommand;

public interface FfmpegNarratedVideoMixService {

    DubbedVideoBo mixNarration(MixNarratedVideoCommand command);
}
