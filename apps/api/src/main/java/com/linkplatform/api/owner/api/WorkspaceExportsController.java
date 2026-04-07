package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.owner.application.WorkspaceExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/current/exports")
public class WorkspaceExportsController {

    private final OwnerAccessService ownerAccessService;
    private final WorkspaceExportService workspaceExportService;

    public WorkspaceExportsController(
            OwnerAccessService ownerAccessService,
            WorkspaceExportService workspaceExportService) {
        this.ownerAccessService = ownerAccessService;
        this.workspaceExportService = workspaceExportService;
    }

    @GetMapping
    public WorkspaceExportPageResponse list(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.EXPORTS_READ);
        return new WorkspaceExportPageResponse(workspaceExportService.list(context).stream().map(WorkspaceExportResponse::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceExportResponse create(
            @RequestBody(required = false) CreateWorkspaceExportRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.EXPORTS_WRITE);
        return WorkspaceExportResponse.from(workspaceExportService.requestExport(
                context,
                requestBody != null && Boolean.TRUE.equals(requestBody.includeClicks()),
                requestBody == null || requestBody.includeSecurityEvents() == null || requestBody.includeSecurityEvents(),
                requestBody == null || requestBody.includeAbuseCases() == null || requestBody.includeAbuseCases(),
                requestBody == null || requestBody.includeWebhooks() == null || requestBody.includeWebhooks()));
    }

    @GetMapping("/{exportId}")
    public WorkspaceExportResponse get(
            @PathVariable long exportId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.EXPORTS_READ);
        return WorkspaceExportResponse.from(workspaceExportService.get(context, exportId));
    }

    @GetMapping("/{exportId}/download")
    public WorkspaceExportDownloadResponse download(
            @PathVariable long exportId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.EXPORTS_READ);
        var exportRecord = workspaceExportService.get(context, exportId);
        return new WorkspaceExportDownloadResponse(exportRecord.id(), exportRecord.createdAt(), exportRecord.completedAt(), exportRecord.payload());
    }
}
