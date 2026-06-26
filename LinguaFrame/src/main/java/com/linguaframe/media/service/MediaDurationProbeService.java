package com.linguaframe.media.service;

import com.linguaframe.media.domain.bo.MediaDurationProbeCommand;
import com.linguaframe.media.domain.bo.MediaDurationProbeResult;

public interface MediaDurationProbeService {

    MediaDurationProbeResult probeDuration(MediaDurationProbeCommand command);
}
