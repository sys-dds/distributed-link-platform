package com.linkplatform.api.projection;

import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/projection-jobs")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class ProjectionJobsController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ProjectionJobService projectionJobService;
    private final ProjectionJobStore projectionJobStore;
    private final OwnerAccessService ownerAccessService;

    public ProjectionJobsController(
            ProjectionJobService projectionJobService,
            ProjectionJobStore projectionJobStore,
            OwnerAccessService ownerAccessService) {
        this.projectionJobService = projectionJobService;
        this.projectionJobStore = projectionJobStore;
        this.ownerAccessService = ownerAccessService;
    }

    @PostMapping
    public ProjectionJobResponse createJob(
            @RequestBody CreateProjectionJobRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        return toResponse(projectionJobService.createJob(request.jobType()));
    }

    @GetMapping("/{id}")
    public ProjectionJobResponse getJob(
            @PathVariable long id,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        return projectionJobStore.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Projection job not found: " + id));
    }

    @GetMapping
    public List<ProjectionJobResponse> listJobs(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpServletRequest) {
        ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr());
        validateLimit(limit);
        return projectionJobStore.findRecent(limit).stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private ProjectionJobResponse toResponse(ProjectionJob job) {
        return new ProjectionJobResponse(
                job.id(),
                job.jobType(),
                job.status(),
                job.requestedAt(),
                job.startedAt(),
                job.completedAt(),
                job.processedCount(),
                job.checkpointId(),
                job.errorSummary());
    }
}
