package com.linguaframe.common.security;

import com.linguaframe.common.config.LinguaFrameProperties;
import org.springframework.stereotype.Service;

@Service
public class ConfiguredDemoOwnerIdentityService implements DemoOwnerIdentityService {

    private final LinguaFrameProperties properties;

    public ConfiguredDemoOwnerIdentityService(LinguaFrameProperties properties) {
        this.properties = properties;
    }

    @Override
    public String currentOwnerId() {
        return properties.getDemo().getOwnerId();
    }
}
