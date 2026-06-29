package com.linguaframe.demo.service;

import com.linguaframe.demo.domain.vo.NarrationDemoPresetVo;

import java.util.List;
import java.util.Optional;

public interface NarrationDemoPresetService {

    List<NarrationDemoPresetVo> listPresets();

    Optional<NarrationDemoPresetVo> findByProfileId(String profileId);

    Optional<NarrationDemoPresetVo> findById(String presetId);
}
