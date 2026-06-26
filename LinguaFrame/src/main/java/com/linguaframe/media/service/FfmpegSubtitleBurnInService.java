package com.linguaframe.media.service;

import com.linguaframe.media.domain.bo.BurnInSubtitlesCommand;
import com.linguaframe.media.domain.bo.BurnedVideoBo;

public interface FfmpegSubtitleBurnInService {

    BurnedVideoBo burnInSubtitles(BurnInSubtitlesCommand command);
}
