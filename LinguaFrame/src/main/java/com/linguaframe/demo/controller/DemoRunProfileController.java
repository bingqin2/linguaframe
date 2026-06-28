package com.linguaframe.demo.controller;

import com.linguaframe.demo.domain.vo.DemoRunProfileVo;
import com.linguaframe.demo.service.DemoRunProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/demo-run-profiles")
@Tag(name = "Demo Run Profiles", description = "Read-only upload presets for repeatable LinguaFrame demos.")
public class DemoRunProfileController {

    private final DemoRunProfileService profileService;

    public DemoRunProfileController(DemoRunProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "List built-in demo run profiles")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Built-in demo run profiles were returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public List<DemoRunProfileVo> listProfiles() {
        return profileService.listProfiles();
    }
}
