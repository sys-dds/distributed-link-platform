package com.linkplatform.api.projection;

import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.JdbcOperatorActionLogStore;
import com.linkplatform.api.owner.application.OperatorActionLogStore;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.owner.application.WorkspaceAccessDeniedException;
import com.linkplatform.api.owner.application.WorkspaceStore;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/projection-jobs")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class ProjectionJobsController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ProjectionJobService projectionJobService;
    private final OwnerAccessService ownerAccessService;
    private final WorkspaceStore workspaceStore;
    private final OperatorActionLogStore operatorActionLogStore;

    public ProjectionJobsController(
            ProjectionJobService projectionJobService,
            OwnerAccessService ownerAccessService,
            WorkspaceStore workspaceStore,
            OperatorActionLogStore operatorActionLogStore) {
        this.projectionJobService = projectionJobService;
        this.ownerAccessService = ownerAccessService;
        this.workspaceStore = workspaceStore;
        this.operatorActionLogStore = operatorActionLogStore;
    }

    @PostMapping
    public ProjectionJobResponse createJob(
            @RequestBody CreateProjectionJobRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_WRITE);
        Long scopedWorkspaceId = resolveRequestedWorkspaceId(context, JdbcOperatorActionLogStore.sanitizeWorkspaceSlug(request.workspaceSlug()));
        String safeNote = JdbcOperatorActionLogStore.sanitizeNote(request.operatorNote());
        ProjectionJob job = projectionJobService.createJob(
                request.jobType(),
                request.ownerId(),
                scopedWorkspaceId,
                request.slug(),
                request.from(),
                request.to(),
                context.ownerId(),
                safeNote);
        operatorActionLogStore.record(
                scopedWorkspaceId,
                context.ownerId(),
                "PROJECTION",
                "projection_job_create",
                request.slug(),
                null,
                job.id(),
                safeNote,
                job.requestedAt());
        return toResponse(job, context);
    }

    @GetMapping("/{id}")
    public ProjectionJobResponse getJob(
            @PathVariable long id,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_READ);
        return projectionJobService.findVisibleJob(id, context.workspaceId(), context.ownerId(), context.personalWorkspace())
                .map(job -> toResponse(job, context))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Projection job not found: " + id));
    }

    @GetMapping
    public List<ProjectionJobResponse> listJobs(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                ApiKeyScope.OPS_READ);
        validateLimit(limit);
        return projectionJobService.findRecentVisibleJobs(limit, context.workspaceId(), context.ownerId(), context.personalWorkspace()).stream()
                .map(job -> toResponse(job, context))
                .toList();
    }

    private ProjectionJobResponse toResponse(ProjectionJob job, WorkspaceAccessContext context) {
        return ProjectionJobResponse.from(job, context);
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private Long resolveRequestedWorkspaceId(WorkspaceAccessContext context, String requestWorkspaceSlug) {
        if (requestWorkspaceSlug == null || requestWorkspaceSlug.isBlank()) {
            return context.workspaceId();
        }
        String normalized = requestWorkspaceSlug.trim();
        if (!context.workspaceSlug().equals(normalized)) {
            throw new WorkspaceAccessDeniedException("Selected workspace is not available to this operator request");
        }
        return workspaceStore.findBySlug(normalized)
                .map(com.linkplatform.api.owner.application.WorkspaceRecord::id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found: " + normalized));
    }
}
