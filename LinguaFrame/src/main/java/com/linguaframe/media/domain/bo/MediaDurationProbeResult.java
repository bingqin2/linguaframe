package com.linguaframe.media.domain.bo;

public record MediaDurationProbeResult(
        double durationSeconds
) {

    public int durationSecondsRoundedUp() {
        return (int) Math.ceil(durationSeconds);
    }
}
