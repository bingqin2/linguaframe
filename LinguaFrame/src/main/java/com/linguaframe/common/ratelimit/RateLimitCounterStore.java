package com.linguaframe.common.ratelimit;

import java.time.Duration;

public interface RateLimitCounterStore {

    long increment(String key, Duration ttl);
}
