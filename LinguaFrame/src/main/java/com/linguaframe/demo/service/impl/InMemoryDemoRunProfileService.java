package com.linguaframe.demo.service.impl;

import com.linguaframe.demo.domain.vo.DemoRunProfileVo;
import com.linguaframe.demo.service.DemoRunProfileService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class InMemoryDemoRunProfileService implements DemoRunProfileService {

    private static final List<DemoRunProfileVo> PROFILES = List.of(
            new DemoRunProfileVo(
                    "quick-baseline",
                    "Quick baseline",
                    "Fast default demo run with no glossary or extra polishing.",
                    "zh-CN",
                    "",
                    "NATURAL",
                    "STANDARD",
                    "OFF",
                    ""
            ),
            new DemoRunProfileVo(
                    "tears-showcase",
                    "Tears showcase",
                    "Presentation profile for Tears of Steel with terminology and readable burned subtitles.",
                    "zh-CN",
                    "",
                    "FORMAL",
                    "HIGH_CONTRAST",
                    "BALANCED",
                    """
                            Maya => 玛雅
                            Tears of Steel => 钢铁之泪
                            Thom => 汤姆
                            """
            ),
            new DemoRunProfileVo(
                    "concise-review",
                    "Concise review",
                    "Compact subtitle review profile with larger burned subtitles and strict cleanup.",
                    "zh-CN",
                    "",
                    "CONCISE",
                    "LARGE",
                    "STRICT",
                    ""
            )
    );

    @Override
    public List<DemoRunProfileVo> listProfiles() {
        return PROFILES;
    }

    @Override
    public Optional<DemoRunProfileVo> findById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return PROFILES.stream()
                .filter(profile -> profile.id().equals(normalized))
                .findFirst();
    }

    @Override
    public String normalizeProfileId(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (findProfile(normalized).isEmpty()) {
            throw new IllegalArgumentException("Unknown demo profile: " + id);
        }
        return normalized;
    }

    private Optional<DemoRunProfileVo> findProfile(String normalizedId) {
        return PROFILES.stream()
                .filter(profile -> profile.id().equals(normalizedId))
                .findFirst();
    }
}
