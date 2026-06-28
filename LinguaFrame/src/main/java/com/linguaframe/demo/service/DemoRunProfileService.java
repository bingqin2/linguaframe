package com.linguaframe.demo.service;

import com.linguaframe.demo.domain.vo.DemoRunProfileVo;

import java.util.List;
import java.util.Optional;

public interface DemoRunProfileService {

    List<DemoRunProfileVo> listProfiles();

    Optional<DemoRunProfileVo> findById(String id);

    String normalizeProfileId(String id);
}
