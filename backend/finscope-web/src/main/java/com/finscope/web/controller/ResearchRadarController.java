package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.radar.ResearchRadarService;
import com.finscope.service.radar.ResearchRadarView;
import com.finscope.service.radar.RadarEventWorkspaceService;
import com.finscope.service.radar.RadarResearchLinkService;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.web.request.RadarObservationRequest;
import com.finscope.web.request.UpdateRadarEventStateRequest;
import com.finscope.web.request.RadarResearchLinkRequest;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/research-radar")
public class ResearchRadarController {
    private final ResearchRadarService service;
    private final RadarEventWorkspaceService workspace;
    private final RadarResearchLinkService researchLinks;
    public ResearchRadarController(ResearchRadarService service, RadarEventWorkspaceService workspace,
                                   RadarResearchLinkService researchLinks){
        this.service=service; this.workspace=workspace; this.researchLinks=researchLinks;
    }

    @GetMapping
    public ApiResponse<ResearchRadarView> radar(@RequestParam(defaultValue="ALL") String category,
                                                @RequestParam(defaultValue="false") boolean watchlistOnly,
                                                @RequestParam(defaultValue="20") int limit){
        return ApiResponses.success(service.load(category,watchlistOnly,limit));
    }

    @GetMapping("/events/{id}")
    public ApiResponse<ResearchRadarView.EventDetail> detail(@PathVariable Long id){
        return ApiResponses.success(service.detail(id));
    }

    @PostMapping("/events/{id}/interpretation")
    public ApiResponse<ResearchRadarView.InterpretationView> requestInterpretation(@PathVariable Long id){
        return ApiResponses.success(service.requestInterpretation(id));
    }

    @PatchMapping("/events/{id}/state")
    public ApiResponse<RadarEventWorkspace.State> updateState(@PathVariable Long id,
                                                              @RequestBody UpdateRadarEventStateRequest request) {
        return ApiResponses.success(workspace.updateState(id, request.getRead(), request.getFollowed(), request.getDisposition()));
    }

    @GetMapping("/events/{id}/observations")
    public ApiResponse<List<RadarEventWorkspace.Observation>> observations(@PathVariable Long id) {
        return ApiResponses.success(workspace.observations(id));
    }

    @PostMapping("/events/{id}/observations")
    public ApiResponse<RadarEventWorkspace.Observation> addObservation(@PathVariable Long id,
                                                                       @RequestBody RadarObservationRequest request) {
        return ApiResponses.success(workspace.addObservation(id, request.getContent()));
    }

    @PatchMapping("/events/{id}/observations/{observationId}")
    public ApiResponse<RadarEventWorkspace.Observation> updateObservation(@PathVariable Long id,
                                                                          @PathVariable Long observationId,
                                                                          @RequestBody RadarObservationRequest request) {
        return ApiResponses.success(workspace.updateObservation(id, observationId, request.getStatus()));
    }

    @DeleteMapping("/events/{id}/observations/{observationId}")
    public ResponseEntity<Void> deleteObservation(@PathVariable Long id, @PathVariable Long observationId) {
        workspace.deleteObservation(id, observationId); return ResponseEntity.noContent().build();
    }

    @PostMapping("/events/{id}/research-links/{runId}")
    public ApiResponse<RadarEventWorkspace.ResearchLink> linkResearch(@PathVariable Long id,@PathVariable Long runId,
                                                                      @RequestBody(required=false) RadarResearchLinkRequest request){
        return ApiResponses.success(researchLinks.link(id,runId,request==null?null:request.getQuestion()));
    }
}
