package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.owner.application.WorkspaceExportService;
import com.linkplatform.api.owner.application.WorkspaceImportService;
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
@RequestMapping("/api/v1/workspaces/current")
public class WorkspaceExportsController {

    private final OwnerAccessService ownerAccessService;
    private final WorkspaceExportService workspaceExportService;
    private final WorkspaceImportService workspaceImportService;

    public WorkspaceExportsController(
            OwnerAccessService ownerAccessService,
            WorkspaceExportService workspaceExportService,
            WorkspaceImportService workspaceImportService) {
        this.ownerAccessService = ownerAccessService;
        this.workspaceExportService = workspaceExportService;
        this.workspaceImportService = workspaceImportService;
    }

    @GetMapping("/exports")
    public WorkspaceExportPageResponse list(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.EXPORTS_READ);
        return new WorkspaceExportPageResponse(workspaceExportService.list(context).stream().map(WorkspaceExportResponse::from).toList());
    }

    @PostMapping("/exports")
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

    @GetMapping("/exports/{exportId}")
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

    @GetMapping("/exports/{exportId}/download")
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

    @GetMapping("/imports")
    public WorkspaceImportPageResponse listImports(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.EXPORTS_READ);
        return new WorkspaceImportPageResponse(workspaceImportService.list(context).stream().map(WorkspaceImportResponse::from).toList());
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceImportResponse createImport(
            @RequestBody(required = false) CreateWorkspaceImportRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.EXPORTS_WRITE);
        return WorkspaceImportResponse.from(workspaceImportService.requestImport(
                context,
                requestBody == null ? null : requestBody.sourceExportId(),
                requestBody == null ? null : requestBody.payloadJson(),
                requestBody == null ? null : requestBody.dryRun(),
                requestBody == null ? null : requestBody.overwriteConflicts()));
    }

    @GetMapping("/imports/{importId}")
    public WorkspaceImportResponse getImport(
            @PathVariable long importId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.EXPORTS_READ);
        return WorkspaceImportResponse.from(workspaceImportService.get(context, importId));
    }

    @PostMapping("/imports/{importId}/apply")
    public WorkspaceImportResponse applyImport(
            @PathVariable long importId,
            @RequestBody(required = false) ApplyWorkspaceImportRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.EXPORTS_WRITE);
        return WorkspaceImportResponse.from(workspaceImportService.applyImport(context, importId));
    }
}
