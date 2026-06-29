package com.linguaframe.demo.controller;

import com.linguaframe.demo.domain.vo.NarrationDemoPresetVo;
import com.linguaframe.demo.domain.vo.DemoRunProfileVo;
import com.linguaframe.demo.service.DemoRunProfileService;
import com.linguaframe.demo.service.NarrationDemoPresetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/demo-run-profiles")
@Tag(name = "Demo Run Profiles", description = "Read-only upload presets for repeatable LinguaFrame demos.")
public class DemoRunProfileController {

    private final DemoRunProfileService profileService;
    private final NarrationDemoPresetService narrationPresetService;

    public DemoRunProfileController(
            DemoRunProfileService profileService,
            NarrationDemoPresetService narrationPresetService
    ) {
        this.profileService = profileService;
        this.narrationPresetService = narrationPresetService;
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

    @GetMapping("/narration-presets")
    @Operation(summary = "List built-in narration demo presets")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Built-in narration demo presets were returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public List<NarrationDemoPresetVo> listNarrationPresets() {
        return narrationPresetService.listPresets();
    }

    @GetMapping("/{profileId}/narration-preset")
    @Operation(summary = "Get the narration demo preset linked to a demo run profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A narration demo preset was returned."),
            @ApiResponse(responseCode = "204", description = "The profile has no narration demo preset."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ResponseEntity<NarrationDemoPresetVo> getNarrationPreset(
            @PathVariable String profileId
    ) {
        return narrationPresetService.findByProfileId(profileId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
